@echo off
cd /d C:\campus-vehicle
set "HOMEPATH=C:\campus-vehicle\ocr-home"
set "USERPROFILE=C:\campus-vehicle\ocr-home"
set "PYTHONIOENCODING=utf-8"
"C:\campus-vehicle\ocr-venv\Scripts\python.exe" -m uvicorn ocr_server:app --host 127.0.0.1 --port 8715 >> "C:\campus-vehicle\ocr.out.log" 2>&1
