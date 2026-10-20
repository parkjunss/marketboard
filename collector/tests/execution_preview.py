"""Production execution router only: no live data jobs, loopback bound by CLI."""
from fastapi import FastAPI
from app.execution_api import router

app = FastAPI()
app.include_router(router)
