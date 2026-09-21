@echo off
setlocal
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven is required but was not found on PATH. Install Maven or use an IDE with Maven support.
  exit /b 1
)
mvn %*
endlocal
