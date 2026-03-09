import sys
import threading

from agent.custom_mcp.mcp_server import mcp_main
import subprocess
from pathlib import Path
import atexit




def main(app_path, port=8501):
    # threading.Thread(target=mcp_main).start()
    # print("MCP服务已启动（异步线程）")

    cmd = [
        sys.executable, "-m",
        "streamlit","run",
        str(app_path),
        # "--server.port", str(port),
        # "--server.headless", "true",
        # '--server.enableCORS', 'false',  # 解决跨域问题（可选）
        # '--server.enableXsrfProtection', 'false',  # 解决跨域问题（可选）
        # '--server.address', '0.0.0.0'  # 允许外部访问，关键配置
    ]

    try:
        # 执行命令（stdout=subprocess.PIPE 可捕获输出，方便日志）
        process = subprocess.run(
            cmd,
            stdout=None,
            stderr=None,
            text=True
        )
        # 打印启动日志
        print(f"Streamlit应用已启动：http://localhost:{port}")
        print(f"进程ID：{process.pid}")

        return process  # 返回进程对象，方便后续终止

    except Exception as e:
        raise RuntimeError(f"启动Streamlit失败：{str(e)}")


if __name__ == '__main__':

    script_dir = Path(__file__).parent
    app_path = script_dir/"streamlit-ex/streamlit/chat_ui.py"
    process = main(app_path)
    def cleanup():
        process.terminate()
        process.wait()
    atexit.register(cleanup)
