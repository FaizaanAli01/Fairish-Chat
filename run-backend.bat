@echo off
rem Start the Fairish Chat backend
cd backend
if not exist .\mvnw.cmd (
    echo Maven wrapper not found. Ensure you have mvnw.cmd in the backend directory.
    exit /b 1
)
call .\mvnw.cmd spring-boot:run
