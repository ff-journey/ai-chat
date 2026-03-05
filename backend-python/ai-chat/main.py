import threading

from agent.mcp.mcp_server import mcp_main
import subprocess
import os
from pathlib import Path


def main(app_path, port=8501):

    threading.Thread(target=mcp_main).start()
    print("MCP服务已启动（异步线程）")
    cmd = [
        "run",
        app_path,
        "--server.port", str(port),
        "--server.headless", "true"
    ]
    try:
        # 执行命令（stdout=subprocess.PIPE 可捕获输出，方便日志）
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )

        # 打印启动日志
        print(f"Streamlit应用已启动：http://localhost:{port}")
        print(f"进程ID：{process.pid}")

        # 可选：等待进程结束并获取输出（若需阻塞执行）
        # stdout, stderr = process.communicate()
        # print("输出：", stdout)
        # print("错误：", stderr)

        return process  # 返回进程对象，方便后续终止

    except Exception as e:
        raise RuntimeError(f"启动Streamlit失败：{str(e)}")


if __name__ == '__main__':
    script_dir = Path(__file__).parent
    app_path = script_dir/"streamlit-ex"/"streamlit"/"chat_ui.py"
    process = main(app_path)

