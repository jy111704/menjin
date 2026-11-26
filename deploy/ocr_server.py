import cv2
import numpy as np
import hyperlpr3 as lpr3
from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse

app = FastAPI(title="CampusPlateOCR")

catcher = lpr3.LicensePlateCatcher(detect_level=lpr3.DETECT_LEVEL_HIGH)

TYPE_LIST = [
    "蓝牌", "黄牌单层", "白牌单层", "绿牌新能源", "黑牌港澳",
    "香港单层", "香港双层", "澳门单层", "澳门双层", "黄牌双层",
]


@app.get("/")
def health():
    return {"ok": True, "service": "campus-plate-ocr"}


@app.post("/api/v1/rec")
async def rec(file: UploadFile = File(...)):
    data = await file.read()
    if not data:
        return JSONResponse({"ok": False, "msg": "empty file"}, status_code=400)

    image = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        return JSONResponse({"ok": False, "msg": "invalid image"}, status_code=400)

    plates = catcher(image)
    result = []
    for code, conf, plate_type, box in plates:
        try:
            type_index = int(plate_type)
            type_name = TYPE_LIST[type_index] if 0 <= type_index < len(TYPE_LIST) else str(plate_type)
        except Exception:
            type_name = str(plate_type)
        result.append({
            "code": str(code),
            "conf": float(conf),
            "plate_type": type_name,
            "box": [int(v) for v in box],
        })
    return {"ok": True, "plate_list": result}
