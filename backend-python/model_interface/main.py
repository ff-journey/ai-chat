import numpy as np
from fastapi import FastAPI, HTTPException, Request, UploadFile, File
from pydantic import BaseModel
from PIL import Image
import io
import os
import time
import logging
from logging.handlers import RotatingFileHandler
import torch
import torchvision.transforms as transforms
from model_repo import FeiyanModel

# ===================== 日志配置 =====================
LOG_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "logs")
os.makedirs(LOG_DIR, exist_ok=True)
LOG_FILE = os.path.join(LOG_DIR, "cnn-app.log")

logger = logging.getLogger("cnn")
logger.setLevel(logging.INFO)
formatter = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s", datefmt="%Y-%m-%d %H:%M:%S")

# 文件handler: 10MB轮转, 保留5个备份
file_handler = RotatingFileHandler(LOG_FILE, maxBytes=10*1024*1024, backupCount=5, encoding="utf-8")
file_handler.setFormatter(formatter)
logger.addHandler(file_handler)

# stdout handler (被部署脚本重定向到cnn.log)
stream_handler = logging.StreamHandler()
stream_handler.setFormatter(formatter)
logger.addHandler(stream_handler)

# ===================== 【必填】修改为你的模型参数 =====================
MODEL_PATH = "./models/feiyan_distillation.pth"  # 你的.pth模型路径
# MODEL_PATH = "./models/model_feiyan.pth"  # 你的.pth模型路径
IMAGE_SIZE = (512, 512)  # 训练时的图片尺寸(必须一致)
IS_GRAYSCALE = True  # 肺炎X光一般是灰度图=True

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"  # 自动用GPU/CPU
label_map = {"NORMAL": 0, "COVID": 1, "Viral_Pneumonia": 2}
CLASS_NAMES = ["正常", "新冠肺炎", "病毒型肺炎"]
# ======================================================================

# 初始化FastAPI
app = FastAPI(title="肺炎识别PyTorch接口", version="1.0")

# ===================== 加载PyTorch模型 =====================
model = None
try:
    # 1. 初始化你的CNN模型结构（必须和训练时完全一样！）
    model = FeiyanModel()  # 替换成你的模型类

    # 2. 加载.pth权重（两种加载方式二选一）
    # 方式1：仅加载权重（最常用，训练时用model.state_dict()保存）
    model.load_state_dict(torch.load(MODEL_PATH, map_location=DEVICE))

    # 方式2：加载整个模型（训练时用torch.save(model)保存）
    # model = torch.load(MODEL_PATH, map_location=DEVICE)

    # 3. 切换为推理模式
    model.eval()
    model.to(DEVICE)
    logger.info(f"PyTorch模型加载成功! 运行设备: {DEVICE}")
except Exception as e:
    logger.error(f"模型加载失败: {str(e)}")
    raise RuntimeError(f"模型加载失败: {str(e)}")


# ===================== 图片预处理（和训练100%一致） =====================
def get_transform():
    """构建和训练完全相同的预处理流程"""
    transform_list = []

    # 灰度图/RGB
    if IS_GRAYSCALE:
        transform_list.append(transforms.Grayscale(num_output_channels=1))
    else:
        transform_list.append(transforms.Grayscale(num_output_channels=3))

    # 尺寸 + 张量转换 + 归一化
    transform_list.extend([
        transforms.Resize(IMAGE_SIZE),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.506], std=[0.221]),  # 训练时的归一化参数
    ])
    return transforms.Compose(transform_list)


# 预定义预处理
transform = get_transform()


class PredictRequest(BaseModel):
    file_path: str

def is_xray_image(image: Image.Image) -> bool:
    """简单判断是否像X光片：灰度图且亮度分布符合X光特征"""
    gray = image.convert("L")
    img_array = np.array(gray)

    # X光片特征：整体偏暗，对比度高
    mean_brightness = img_array.mean()
    std_brightness = img_array.std()

    # 均值偏低（X光片整体较暗），标准差较高（对比度强）
    return mean_brightness < 180 and std_brightness > 40

# ===================== 共享推理逻辑 =====================
def run_inference(image: Image.Image, source_desc: str) -> dict:
    """对已打开的 PIL Image 执行推理，返回结果字典。"""
    start_time = time.time()
    try:
        if not is_xray_image(image):
            return {"result": "非X光图片，无法识别", "confidence": 0.0}

        img_tensor = transform(image).unsqueeze(0).to(DEVICE)

        with torch.no_grad():
            output = model(img_tensor)
            confidence = torch.softmax(output, dim=1)
            logger.info(f"logits: {output}\nsoftmax: {confidence}")

        confidences = {CLASS_NAMES[i]: round(confidence[0][i].item(), 4) for i in range(len(CLASS_NAMES))}
        best_class = max(confidences, key=confidences.get)
        best_conf = confidences[best_class]

        elapsed = round(time.time() - start_time, 3)

        if best_conf < 0.9:
            result = {"result": "无法识别肺炎分类, 或高度疑似非X光图片", "confidence": best_conf}
            logger.info(f"预测完成 [{elapsed}s] ({source_desc}): {result} | 各类置信度: {confidences}")
            return result

        result = {"result": best_class, "confidence": best_conf}
        logger.info(f"预测完成 [{elapsed}s] ({source_desc}): {result} | 各类置信度: {confidences}")
        return result

    except Exception as e:
        elapsed = round(time.time() - start_time, 3)
        logger.error(f"预测失败 [{elapsed}s] ({source_desc}): {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"预测失败：{str(e)}")


# ===================== 预测接口 =====================
@app.post("/api/pneumonia/predict", summary="肺炎图片识别(文件路径)")
async def predict(request: PredictRequest):
    logger.info(f"收到预测请求: file_path={request.file_path}")

    if not os.path.exists(request.file_path):
        logger.warning(f"文件不存在: {request.file_path}")
        raise HTTPException(status_code=400, detail=f"文件不存在: {request.file_path}")

    if not request.file_path.lower().endswith((".jpg", ".jpeg", ".png")):
        logger.warning(f"不支持的文件格式: {request.file_path}")
        raise HTTPException(status_code=400, detail="仅支持jpg/png图片")

    image = Image.open(request.file_path).convert("RGB")
    return run_inference(image, f"path={request.file_path}")


@app.post("/api/pneumonia/predict/upload", summary="肺炎图片识别(文件上传)")
async def predict_upload(file: UploadFile = File(...)):
    logger.info(f"收到上传预测请求: filename={file.filename}, content_type={file.content_type}")

    if not (file.filename or "").lower().endswith((".jpg", ".jpeg", ".png")):
        raise HTTPException(status_code=400, detail="仅支持jpg/png图片")

    contents = await file.read()
    image = Image.open(io.BytesIO(contents)).convert("RGB")
    return run_inference(image, f"upload={file.filename}")


# 健康检查
@app.get("/")
async def root():
    logger.info("健康检查请求")
    return {"message": "服务正常！访问 http://localhost:9801/docs 测试"}

if __name__ == '__main__':
    import uvicorn
    logger.info("启动CNN推理服务 http://127.0.0.1:9801")
    uvicorn.run(app, host="127.0.0.1", port=9801, log_level="info")




