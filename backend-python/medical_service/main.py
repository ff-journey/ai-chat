"""
Medical Diagnosis Service — OpenAI-compatible /v1/chat/completions API
Serves the fine-tuned Qwen3 medical model via transformers.
Port: 9901 (same as previous vLLM deployment, Java backend zero changes)
"""

import time
import uuid
import torch
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from transformers import AutoModelForCausalLM, AutoTokenizer

# ── Config ───────────────────────────────────────────────────────────────────
MODEL_PATH = r"G:\python_code\ai_basic\ai\transformers_basic\framework\qwen3_sft_full_medical\merged2"
DEFAULT_MODEL_NAME = "qwen3-medical"
PORT = 9901
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
DTYPE = torch.float16 if DEVICE == "cuda" else torch.float32

# ── Global model / tokenizer ────────────────────────────────────────────────
model = None
tokenizer = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model, tokenizer
    print(f"Loading model from {MODEL_PATH} on {DEVICE} ({DTYPE}) ...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_PATH, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_PATH,
        torch_dtype=DTYPE,
        device_map=DEVICE,
        trust_remote_code=True,
    )
    model.eval()
    print("Model loaded.")
    yield
    del model, tokenizer
    torch.cuda.empty_cache()


app = FastAPI(title="Medical Diagnosis Service", lifespan=lifespan)


# ── Request / Response schemas (OpenAI-compatible) ──────────────────────────
class Message(BaseModel):
    role: str
    content: str


class ChatCompletionRequest(BaseModel):
    model: str = DEFAULT_MODEL_NAME
    messages: list[Message]
    temperature: float = 0.6
    top_p: float = 0.95
    top_k: int = 20
    max_tokens: int = 2048


class Choice(BaseModel):
    index: int = 0
    message: Message
    finish_reason: str = "stop"


class Usage(BaseModel):
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0


class ChatCompletionResponse(BaseModel):
    id: str = Field(default_factory=lambda: f"chatcmpl-{uuid.uuid4().hex[:12]}")
    object: str = "chat.completion"
    created: int = Field(default_factory=lambda: int(time.time()))
    model: str = DEFAULT_MODEL_NAME
    choices: list[Choice]
    usage: Usage = Usage()


# ── Endpoint ─────────────────────────────────────────────────────────────────
@app.post("/v1/chat/completions", response_model=ChatCompletionResponse)
async def chat_completions(req: ChatCompletionRequest):
    if not model or not tokenizer:
        raise HTTPException(status_code=503, detail="Model not loaded")

    # Build prompt via chat template
    messages = [{"role": m.role, "content": m.content} for m in req.messages]
    text = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)

    inputs = tokenizer(text, return_tensors="pt").to(DEVICE)
    prompt_len = inputs["input_ids"].shape[-1]

    with torch.no_grad():
        outputs = model.generate(
            **inputs,
            max_new_tokens=req.max_tokens,
            temperature=req.temperature,
            top_p=req.top_p,
            top_k=req.top_k,
            do_sample=req.temperature > 0,
        )

    new_tokens = outputs[0][prompt_len:]
    reply = tokenizer.decode(new_tokens, skip_special_tokens=True)

    return ChatCompletionResponse(
        model=req.model,
        choices=[Choice(message=Message(role="assistant", content=reply))],
        usage=Usage(
            prompt_tokens=prompt_len,
            completion_tokens=len(new_tokens),
            total_tokens=prompt_len + len(new_tokens),
        ),
    )


@app.get("/health")
async def health():
    return {"status": "ok", "model": DEFAULT_MODEL_NAME, "device": DEVICE}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=PORT)
