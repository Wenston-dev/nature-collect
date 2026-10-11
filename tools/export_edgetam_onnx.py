"""Export EdgeTAM's image segmenter to the two ONNX files the app loads from assets/sam/.

Usage (needs torch, timm, hydra-core, iopath, onnx and the EdgeTAM source on PYTHONPATH):

    git clone https://github.com/facebookresearch/EdgeTAM
    PYTHONPATH=EdgeTAM python tools/export_edgetam_onnx.py \
        --checkpoint EdgeTAM/checkpoints/edgetam.pt \
        --out app/src/main/assets/sam

Only the single-image path of EdgeTAM is exported (no video memory).

Encoder: input "image" [1, 3, 1024, 1024], the photo stretched to 1024x1024
and normalised with ImageNet mean/std; outputs "image_embeddings"
[1, 256, 64, 64] plus the two high-resolution feature maps SAM 2's decoder
uses for finer edges, "high_res_feats_0" [1, 32, 256, 256] and
"high_res_feats_1" [1, 64, 128, 128].

Decoder: same prompt inputs as SAM's standard ONNX decoder (points in image
pixels, labels -1/0/1/2/3, mask_input, has_mask_input, orig_im_size), plus the
two feature maps. Returns all four candidate masks as logits at
"orig_im_size" resolution and their predicted scores.
"""

import argparse
import os
import warnings

import torch
import torch.nn.functional as F
from torch import nn

from sam2.build_sam import build_sam2


class Encoder(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, image):
        m = self.model
        backbone_out = m.forward_image(image)
        _, vision_feats, _, feat_sizes = m._prepare_backbone_features(backbone_out)
        if m.directly_add_no_mem_embed:
            vision_feats[-1] = vision_feats[-1] + m.no_mem_embed
        feats = [
            feat.permute(1, 2, 0).reshape(1, -1, *size)
            for feat, size in zip(vision_feats, feat_sizes)
        ]
        return feats[-1], feats[0], feats[1]


class Decoder(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.prompt_encoder = model.sam_prompt_encoder
        self.mask_decoder = model.sam_mask_decoder
        self.image_size = model.image_size

    def embed_points(self, coords, labels):
        pe = self.prompt_encoder
        coords = coords + 0.5
        coords = coords / self.image_size
        emb = pe.pe_layer._pe_encoding(coords)
        labels = labels.unsqueeze(-1).expand_as(emb)
        emb = emb * (labels != -1)
        emb = emb + pe.not_a_point_embed.weight * (labels == -1)
        for i in range(pe.num_point_embeddings):
            emb = emb + pe.point_embeddings[i].weight * (labels == i)
        return emb

    def forward(self, image_embeddings, high_res_feats_0, high_res_feats_1,
                point_coords, point_labels, mask_input, has_mask_input, orig_im_size):
        # Points arrive in image pixels; SAM 2 stretches the image to a square.
        h, w = orig_im_size[0], orig_im_size[1]
        scale = torch.stack([self.image_size / w, self.image_size / h])
        sparse = self.embed_points(point_coords * scale, point_labels)
        pe = self.prompt_encoder
        dense = has_mask_input * pe.mask_downscaling(mask_input) + (
            1 - has_mask_input
        ) * pe.no_mask_embed.weight.reshape(1, -1, 1, 1)
        masks, scores, _, _ = self.mask_decoder.predict_masks(
            image_embeddings=image_embeddings,
            image_pe=pe.get_dense_pe(),
            sparse_prompt_embeddings=sparse,
            dense_prompt_embeddings=dense,
            repeat_image=False,
            high_res_features=[high_res_feats_0, high_res_feats_1],
        )
        size = orig_im_size.to(torch.int64)
        masks = F.interpolate(masks, size=(size[0], size[1]), mode="bilinear", align_corners=False)
        return masks, scores


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--config", default="configs/edgetam.yaml")
    parser.add_argument("--out", required=True)
    parser.add_argument("--opset", type=int, default=17)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    model = build_sam2(args.config, args.checkpoint, device="cpu", apply_postprocessing=False)
    model.eval()
    warnings.filterwarnings("ignore", category=torch.jit.TracerWarning)

    torch.onnx.export(
        Encoder(model),
        (torch.randn(1, 3, 1024, 1024),),
        os.path.join(args.out, "edgetam_encoder.onnx"),
        input_names=["image"],
        output_names=["image_embeddings", "high_res_feats_0", "high_res_feats_1"],
        opset_version=args.opset,
        do_constant_folding=True,
        dynamo=False,
    )

    dummy = {
        "image_embeddings": torch.randn(1, 256, 64, 64),
        "high_res_feats_0": torch.randn(1, 32, 256, 256),
        "high_res_feats_1": torch.randn(1, 64, 128, 128),
        "point_coords": torch.randint(low=0, high=1024, size=(1, 5, 2), dtype=torch.float),
        "point_labels": torch.randint(low=0, high=4, size=(1, 5), dtype=torch.float),
        "mask_input": torch.randn(1, 1, 256, 256),
        "has_mask_input": torch.tensor([1], dtype=torch.float),
        "orig_im_size": torch.tensor([768, 1024], dtype=torch.float),
    }
    torch.onnx.export(
        Decoder(model),
        tuple(dummy.values()),
        os.path.join(args.out, "edgetam_decoder.onnx"),
        input_names=list(dummy.keys()),
        output_names=["masks", "iou_predictions"],
        dynamic_axes={"point_coords": {1: "num_points"}, "point_labels": {1: "num_points"}},
        opset_version=args.opset,
        do_constant_folding=True,
        dynamo=False,
    )


if __name__ == "__main__":
    main()
