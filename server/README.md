# Horizon Server (yt-dlp backend)

Servidor de extração de áudio pro **Horizon Music**. O app Android chama este
servidor pra obter o link de áudio do YouTube, porque as instâncias
Piped/Invidious públicas morreram e o InnerTube exige API key dinâmica.

## Como rodar

```bash
pip install -r requirements.txt
python server.py          # porta 8080
python server.py 5000     # porta custom
```

## Endpoints

| Endpoint | Descrição |
|----------|-----------|
| `GET /stream?url=<youtube-url>` | Retorna o link de áudio (JSON) |
| `GET /search?q=<query>&limit=10` | Busca no YouTube Music |
| `GET /health` | Health check |

## No app

1. Rode o servidor no PC ou no Termux do celular
2. Descubra o IP: `ifconfig` (Linux) ou `termux-wifi-connectioninfo` (Termux)
3. No Horizon Music → aba Online → toque em **"Configurar servidor de áudio"**
4. Cole o endereço: `http://SEU_IP:8080`

## Deploy grátis (opcional)

Pode subir no [Render](https://render.com) como Web Service:
- Build command: `pip install -r requirements.txt`
- Start command: `python server.py`
- Depois é só colar a URL do Render no app (funciona de qualquer lugar!)
