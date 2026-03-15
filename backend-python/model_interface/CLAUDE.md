# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

This is a FastAPI inference service that exposes a PyTorch CNN model for pneumonia X-ray classification. It classifies chest X-ray images into 3 categories: 正常 (Normal), 新冠肺炎 (COVID-19), 病毒型肺炎 (Viral Pneumonia).

## Commands

```bash
# Install dependencies (requires torch, torchvision, fastapi, uvicorn, pillow)
pip install torch torchvision fastapi uvicorn pillow

# Run the service (starts on http://127.0.0.1:9801)
python main.py

# Interactive API docs
# http://localhost:9801/docs
```

## Architecture

### Model (`model_repo.py`)
`FeiyanModel` is a 4-layer CNN for 3-class classification:
- Input: 1-channel (grayscale) 512x512 image
- Layer 1: Conv2d(1→16, k=11) → BN → ReLU → MaxPool(4)  → 16x128x128
- Layer 2: Conv2d(16→64, k=7) → BN → ReLU → MaxPool(4)  → 64x32x32
- Layer 3: Conv2d(64→96, k=1) → BN → ReLU → MaxPool(4)  → 96x8x8
- Layer 4: Flatten → ReLU → Linear(6144→3)

Weights are loaded from `models/feiyan_distillation.pth` via `state_dict` (not full model save).

### API (`main.py`)
- `POST /api/pneumonia/predict` — accepts `{"file_path": "/abs/path/to/image.jpg"}`, returns confidence dict e.g. `{"正常": 0.99, "新冠肺炎": 0.01, "肺炎": 0.00}`
- `GET /` — health check

### Key Configuration Constants (top of `main.py`)
| Constant | Value | Notes |
|---|---|---|
| `MODEL_PATH` | `./models/feiyan_distillation.pth` | Must match training save path |
| `IMAGE_SIZE` | `(512, 512)` | Must match training input size |
| `IS_GRAYSCALE` | `True` | Grayscale for X-ray |
| Normalize | mean=0.506, std=0.221 | Must match training dataset stats |

If the model architecture or preprocessing is changed, these constants **must** stay consistent with how the model was trained — mismatches will produce wrong predictions silently.
