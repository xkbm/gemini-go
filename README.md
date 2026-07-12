# Gemini Go

**Cliente Android ligero de Google Gemini, optimizado para Android Go Edition y dispositivos de gama baja.**

Gemini Go es una app nativa en Kotlin que ofrece chat conversacional con los modelos Gemini de Google mediante streaming SSE, soporte multimodal (texto + imágenes), persistencia de conversaciones y un renderizado Markdown propio — todo sin dependencias pesadas.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![API](https://img.shields.io/badge/minSdk-26-brightgreen)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.23-purple)](https://kotlinlang.org)

---

## Tabla de contenidos

- [Características](#características)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Modelos disponibles](#modelos-disponibles)
- [Requisitos](#requisitos)
- [Primeros pasos](#primeros-pasos)
- [Configuración](#configuración)
- [Permisos](#permisos)
- [Notas importantes](#notas-importantes)
- [Licencia](#licencia)

---

## Características

- **Chat en streaming** con Gemini vía SSE (Server-Sent Events) — respuestas en tiempo real.
- **Soporte multimodal**: adjunta imágenes a la conversación.
- **Conversaciones persistentes** con Room: historial completo, reanudable.
- **Catálogo de 8 modelos**: selección entre modelos de texto y de imagen (Nano Banana 2 family).
- **Renderizado Markdown propio**: encoder, listas, tablas nativas con scroll horizontal, bloques de código, citas, inline formatting.
- **Generación de imágenes** vía Interactions API (modelos Nano Banana 2) — requiere tier de pago.
- **Detección automática de prompts de imagen**: cuando el modelo de texto activo no soporta imagen nativa, la app envía el prompt a un modelo de imagen compatible (estilo ChatGPT).
- **Ajustes configurables**: API key, modelo activo, temperatura, system prompt.
- **UI con Material Design 3** y ViewBinding, optimizada para pantallas pequeñas y recursos limitados.

---

## Stack tecnológico

| Componente         | Tecnología                                   |
|--------------------|----------------------------------------------|
| Lenguaje           | Kotlin 100%                                  |
| UI                 | Android Views + Material Design 3             |
| Arquitectura       | MVVM + Repository pattern                     |
| Networking         | OkHttp + Gson + streaming SSE                 |
| Persistencia       | Room (conversaciones + mensajes), SharedPreferences |
| Inyección de DI    | Manual (sin Hilt ni Dagger)                   |
| Binding            | ViewBinding                                   |
| Build              | Gradle Groovy DSL + KSP (Room)                |
| Versiones clave    | Kotlin 1.9.23 · Gradle 8.7 · AGP 8.3.2       |
| Java               | 17                                            |

---

## Estructura del proyecto

```
com.gemini.go/
├── GeminiApp.kt                          # Application class
├── data/
│   ├── api/
│   │   ├── GeminiClient.kt               # Cliente HTTP + SSE
│   │   └── PartAdapter.kt                # Adaptador Gson para partes multipart
│   ├── db/
│   │   ├── ConversationDao.kt
│   │   ├── MessageDao.kt
│   │   └── GeminiDatabase.kt             # Base de datos Room
│   ├── model/
│   │   ├── Entities.kt                   # Entidades de Room
│   │   ├── GeminiDto.kt                  # DTOs de la API
│   │   └── GeminiModel.kt               # Catálogo de modelos disponibles
│   └── repo/
│       ├── GeminiRepository.kt           # Lógica de negocio y caché
│       └── PreferencesManager.kt         # Gestión de preferencias
├── ui/
│   ├── chat/
│   │   ├── ChatActivity.kt
│   │   ├── ChatViewModel.kt
│   │   └── MessagesAdapter.kt
│   ├── conversations/
│   │   ├── ConversationsActivity.kt
│   │   └── ConversationsAdapter.kt
│   └── settings/
│       └── SettingsActivity.kt
└── util/
    ├── MarkdownBlockParser.kt            # Parser de bloques MD (texto, tabla, código)
    └── MarkdownRenderer.kt               # Renderer MD -> Spannable
```

---

## Modelos disponibles

### Texto

| Modelo                       | ID interno                      |
|------------------------------|----------------------------------|
| Gemini 3.1 Flash Lite        | `gemini-3.1-flash-lite`         |
| Gemini 2.5 Flash             | `gemini-2.5-flash`              |
| Gemini 2.5 Pro               | `gemini-2.5-pro`                |
| Gemini 2.0 Flash             | `gemini-2.0-flash`              |

### Imagen (Nano Banana 2 family)

| Modelo                          | ID interno                         |
|---------------------------------|-------------------------------------|
| Gemini 3.1 Flash Lite Image     | `gemini-3.1-flash-lite-image`      |
| Gemini 3.1 Flash Image          | `gemini-3.1-flash-image`           |
| Gemini 3 Pro Image              | `gemini-3-pro-image`               |
| Gemini 2.5 Flash Image (legacy) | `gemini-2.5-flash-image`           |

> El modelo por defecto es `gemini-3.1-flash-lite`.

---

## Requisitos

- **Dispositivo**: Android 8.0 (API 26) o superior — compatible con Android Go Edition.
- **JDK**: 17
- **Android Studio**: Hedgehog (2023.1.1) o superior.
- **API key**: necesitas una clave de Google Gemini. Obtén una gratis en [Google AI Studio](https://aistudio.google.com/app/apikey).

---

## Primeros pasos

### 1. Clonar el repositorio

```bash
git clone https://github.com/xkbm/gemini-go.git
cd gemini-go
```

### 2. Compilar

```bash
./gradlew assembleDebug
```

El APK se generará en `app/build/outputs/apk/debug/`.

También puedes abrir el proyecto en Android Studio, sincronizar Gradle y ejecutar sobre un emulador o dispositivo físico.

---

## Configuración

1. Abre la aplicación.
2. Ve a **Settings** (icono de engranaje).
3. Introduce tu **API key** de Gemini (obtenida desde [AI Studio](https://aistudio.google.com/app/apikey)).
4. Selecciona el **modelo** deseado (texto o imagen).
5. Ajusta opcionalmente la **temperatura** y el **system prompt**.
6. Vuelve al chat y empieza a escribir.

---

## Permisos

| Permiso                        | Propósito                                    |
|--------------------------------|----------------------------------------------|
| `INTERNET`                     | Comunicación con la API de Gemini            |
| `READ_MEDIA_IMAGES` (API 33+)  | Adjuntar imágenes desde la galería           |
| `READ_EXTERNAL_STORAGE` (< 33) | Adjuntar imágenes en versiones anteriores    |

---

## Notas importantes

**Generación de imágenes.** La funcionalidad de generación de imágenes requiere una cuenta de pago (Pay-as-you-go). El tier gratuito de Gemini no incluye cuota para los modelos de imagen (`limit: 0`). El código utiliza la Interactions API (`POST /v1beta/interactions` con cabecera `x-goog-api-key`).

**Markdown propio.** Todo el renderizado Markdown es implementación 100% propia, sin librerías externas (Markwon no está incluido). Las tablas se renderizan como `TableLayout` nativo de Android envuelto en un `HorizontalScrollView`. El formato inline dentro de celdas de tabla (`**bold**`, `*italic*`, `` `code` ``, `[links](url)`) se procesa a través de `MarkdownRenderer.render()`.

**Compatibilidad.** Aunque está optimizado para Android Go Edition, la app funciona sin problemas en cualquier dispositivo con Android 8.0 o superior.

---

## Recursos

- [Google AI Studio — Obtener API key](https://aistudio.google.com/app/apikey)
- [Documentación de la API Gemini](https://ai.google.dev/gemini-api/docs)
- [Android Go Edition](https://www.android.com/versions/go-edition/)

---

## Licencia

```
MIT License

Copyright (c) 2026 kbm_

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
