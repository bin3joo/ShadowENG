### 1. 전체 구조
```
GitLab (develop branch)
        │ push
        ▼
Jenkins Webhook
        │
        ▼
Jenkins Pipeline
   1. git checkout
   2. docker-compose build
   3. docker-compose up
        │
        ▼
EC2 Docker Containers
   ├ Jenkins        (host:9090 → container:8080)
   ├ SpringBoot     (host:8080 → container:8080)
   ├ PostgreSQL     (host:5432 → container:5432)
   └ Redis          (host:6379 → container:6379)
        │
        │ HTTP POST (AI_BASE_URL)
        ▼
AI Server (ngrok → 로컬 GPU 머신:8000)
```