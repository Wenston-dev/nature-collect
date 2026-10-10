"""Export MobileSAM to the two ONNX files the app loads from assets/sam/.

Usage (needs torch, timm, onnx and the MobileSAM source on PYTHONPATH):

    git clone https://github.com/ChaoningZhang/MobileSAM
    PYTHONPATH=MobileSAM python tools/export_mobilesam_onnx.py \
        --checkpoint MobileSAM/weights/mobile_sam.pt \
        --out app/src/main/assets/sam

Encoder: input "image" [1, 3, 1024, 1024], already normalised with SAM's
pixel mean/std and zero-padded on the right/bottom; output
"image_embeddings" [1, 256, 64, 64].

Decoder: SAM's standard ONNX prompt decoder returning all four candidate masks
and their scores at "orig_im_size" resolution, so the app can offer sizes.
"""

import argparse
import os
import warnings

import torch
from mobile_sam import sam_model_registry
from mobile_sam.utils.onnx import SamOnnxModel


class EncoderOnly(torch.nn.Module):
    def __init__(self, sam):
        super().__init__()
        self.image_encoder = sam.image_encoder

    def forward(self, image):
        return self.image_encoder(image)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--opset", type=int, default=17)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    sam = sam_model_registry["vit_t"](checkpoint=args.checkpoint)
    sam.eval()
    warnings.filterwarnings("ignore", category=torch.jit.TracerWarning)

    encoder = EncoderOnly(sam)
    torch.onnx.export(
        encoder,
        (torch.randn(1, 3, 1024, 1024),),
        os.path.join(args.out, "mobile_sam_encoder.onnx"),
        input_names=["image"],
        output_names=["image_embeddings"],
        opset_version=args.opset,
        do_constant_folding=True,
        dynamo=False,
    )

    decoder = SamOnnxModel(sam, return_single_mask=False)
    embed_dim = sam.prompt_encoder.embed_dim
    embed_size = sam.prompt_encoder.image_embedding_size
    mask_input_size = [4 * x for x in embed_size]
    dummy = {
        "image_embeddings": torch.randn(1, embed_dim, *embed_size, dtype=torch.float),
        "point_coords": torch.randint(low=0, high=1024, size=(1, 5, 2), dtype=torch.float),
        "point_labels": torch.randint(low=0, high=4, size=(1, 5), dtype=torch.float),
        "mask_input": torch.randn(1, 1, *mask_input_size, dtype=torch.float),
        "has_mask_input": torch.tensor([1], dtype=torch.float),
        "orig_im_size": torch.tensor([1500, 2250], dtype=torch.float),
    }
    torch.onnx.export(
        decoder,
        tuple(dummy.values()),
        os.path.join(args.out, "mobile_sam_decoder.onnx"),
        input_names=list(dummy.keys()),
        output_names=["masks", "iou_predictions", "low_res_masks"],
        dynamic_axes={"point_coords": {1: "num_points"}, "point_labels": {1: "num_points"}},
        opset_version=args.opset,
        do_constant_folding=True,
        dynamo=False,
    )


if __name__ == "__main__":
    main()
