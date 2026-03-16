from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from PIL import Image
import os
import torch
import torchvision.transforms as transforms
from model_repo import FeiyanModel

# ===================== 【必填】修改为你的模型参数 =====================
MODEL_PATH = "./models/feiyan_distillation.pth"  # 你的.pth模型路径
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
    print(f"✅ PyTorch模型加载成功！运行设备：{DEVICE}")
except Exception as e:
    raise RuntimeError(f"❌ 模型加载失败：{str(e)}")


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


# ===================== 预测接口 =====================
@app.post("/api/pneumonia/predict", summary="肺炎图片识别")
async def predict(request: PredictRequest):
    if not os.path.exists(request.file_path):
        raise HTTPException(status_code=400, detail=f"文件不存在: {request.file_path}")

    # 校验文件格式
    if not request.file_path.lower().endswith((".jpg", ".jpeg", ".png")):
        raise HTTPException(status_code=400, detail="仅支持jpg/png图片")

    try:
        # 1. 读取图片
        image = Image.open(request.file_path).convert("RGB")

        # 2. 预处理
        img_tensor = transform(image).unsqueeze(0).to(DEVICE)  # 增加batch维度

        # 3. PyTorch推理（禁用梯度计算，提速）
        with torch.no_grad():
            output = model(img_tensor)
            # 二分类：sigmoid输出概率
            confidence = torch.softmax(output, dim=1)

        confidences = {CLASS_NAMES[i]: round(confidence[0][i].item(), 4) for i in range(len(CLASS_NAMES))}
        best_class = max(confidences, key=confidences.get)
        best_conf = confidences[best_class]

        if best_conf < 0.7:
            return {"result": "无法识别肺炎分类", "confidence": best_conf}

        return {"result": best_class, "confidence": best_conf}

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"预测失败：{str(e)}")


# 健康检查
@app.get("/")
async def root():
    return {"message": "服务正常！访问 http://localhost:9801/docs 测试"}

if __name__ == '__main__':
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=9801)


