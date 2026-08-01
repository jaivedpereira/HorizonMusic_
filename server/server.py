"""
Horizon Music — servidor de extração de áudio (yt-dlp backend)

O app Android chama este servidor pra obter o link de áudio do YouTube,
porque as instâncias Piped/Invidious públicas morreram e o InnerTube
exige API key dinâmica.

Endpoints:
    GET /stream?url=<youtube-url>          → JSON com link de áudio
    GET /search?q=<query>                  → busca no YouTube Music
    GET /health                            → health check

Como rodar (Termux ou PC):
    pip install flask yt-dlp
    python server.py [porta]

No celular com o app:
    - Rode o servidor no Termux: python server.py 8080
    - Descubra o IP: termux-wifi-connectioninfo (ou ifconfig)
    - No app, configure o IP do servidor em Configurações
"""
from __future__ import annotations

import json
import sys

import yt_dlp
from flask import Flask, jsonify, request

app = Flask(__name__)

# Limite de requisições simultâneas por extração
YDL_OPTS = {
    "format": "bestaudio/best",
    "noplaylist": True,
    "quiet": True,
    "no_warnings": True,
    "socket_timeout": 20,
}


@app.route("/health")
def health():
    return jsonify({"ok": True, "service": "horizon-ytdlp"})


@app.route("/stream")
def stream():
    url = request.args.get("url", "").strip()
    if not url or ("youtube.com" not in url and "youtu.be" not in url):
        return jsonify({"error": "URL do YouTube inválida"}), 400

    try:
        with yt_dlp.YoutubeDL(YDL_OPTS) as ydl:
            info = ydl.extract_info(url, download=False)
            if info is None:
                return jsonify({"error": "Não foi possível extrair o vídeo"}), 502

            formats = info.get("formats") or []
            # melhor áudio: maior bitrate entre audio-only
            audio = [f for f in formats if f.get("acodec") != "none"]
            audio.sort(key=lambda f: f.get("tbr") or f.get("abr") or 0, reverse=True)
            best = audio[0] if audio else None

            if not best or not best.get("url"):
                return jsonify({"error": "Nenhum formato de áudio encontrado"}), 502

            return jsonify({
                "title": info.get("title", ""),
                "duration": info.get("duration", 0),
                "url": best["url"],
                "ext": best.get("ext", "webm"),
                "bitrate": best.get("tbr") or best.get("abr"),
                "thumbnail": info.get("thumbnail", ""),
                "uploader": info.get("uploader", ""),
            })
    except Exception as e:  # noqa: BLE001
        return jsonify({"error": f"Erro na extração: {e}"}), 502


@app.route("/search")
def search():
    query = request.args.get("q", "").strip()
    if not query:
        return jsonify({"error": "query vazia"}), 400
    limit = min(int(request.args.get("limit", 10)), 20)

    opts = {
        **YDL_OPTS,
        "extract_flat": True,
        "quiet": True,
    }
    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(f"ytsearch{limit}:{query}", download=False)
            entries = info.get("entries") or []
            results = []
            for e in entries[:limit]:
                if not e:
                    continue
                vid = e.get("id")
                if not vid:
                    continue
                results.append({
                    "id": vid,
                    "title": e.get("title", ""),
                    "artist": e.get("uploader") or e.get("channel") or "",
                    "thumbnail": e.get("thumbnail") or "",
                    "url": f"https://www.youtube.com/watch?v={vid}",
                    "duration": e.get("duration", 0),
                })
            return jsonify({"results": results, "count": len(results)})
    except Exception as e:  # noqa: BLE001
        return jsonify({"error": f"Erro na busca: {e}"}), 502


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    print(f"🎵 Horizon Music server rodando em http://0.0.0.0:{port}")
    app.run(host="0.0.0.0", port=port, debug=False)
