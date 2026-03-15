from fastapi import FastAPI

app = FastAPI()

@app.get("/")
def home():
    return {"message": "동동(DongDong) 서버가 드디어 숨을 쉬기 시작했습니다!"}

@app.get("/test")
def test():
    return {"status": "success", "info": "FastAPI가 아주 잘 돌아가고 있어요."}