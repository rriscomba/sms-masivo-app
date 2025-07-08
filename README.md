# 📱 SMS Masivo

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![API Level](https://img.shields.io/badge/API-31%2B-blue.svg)](https://developer.android.com/guide/topics/manifest/uses-sdk-element#ApiLevels)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)

Aplicación Android nativa para envío masivo de mensajes SMS desde archivos CSV con verificación de entrega y reportes detallados.

## 🚀 Características

- **📁 Carga CSV** - Importa contactos y mensajes desde archivos CSV
- **📱 Envío masivo** - Procesa SMS por lotes configurables
- **✅ Verificación real** - Confirma envío y entrega de cada mensaje
- **📊 Reportes** - Exporta resultados con fecha/hora y estado
- **⚙️ Configuración** - Ajusta tamaño de lote y delays
- **🔒 Seguro** - Maneja permisos y restricciones de operadoras

## 📥 Instalación

### Desde Releases
1. Descarga el APK desde [Releases](https://github.com/tu-usuario/sms-masivo-app/releases)
2. Instala en Android 12+ (API 31)
3. Concede permisos SMS cuando se soliciten

### Compilar desde código
```bash
git clone https://github.com/tu-usuario/sms-masivo-app.git
cd sms-masivo-app
./gradlew assembleDebug
```

## 📋 Formato CSV

### Estructura
```csv
+5511999999999,Hola Juan! ¿Cómo estás?
+5521888888888,"Mensaje con, comas aquí"
+5531777777777,Recordatorio de cita mañana
```

### Requisitos
- **Separador**: Comas (`,`)
- **Codificación**: UTF-8
- **Sin encabezados**
- **Números**: Con código de país (+55, +1, etc.)
- **Comillas**: Usar `"` si el mensaje contiene comas

## 🔧 Uso

1. **Preparar CSV** - Crea archivo con formato correcto
2. **Cargar archivo** - Toca "Cargar Archivo CSV"
3. **Configurar** - Ajusta lotes y delays (⚙️)
4. **Enviar** - Toca "Enviar SMS" y concede permisos
5. **Monitorear** - Observa progreso en tiempo real
6. **Exportar** - Descarga reporte con resultados

## ⚙️ Configuración Recomendada

| Parámetro | Valor Sugerido | Descripción |
|-----------|----------------|-------------|
| **Tamaño de lote** | 5-10 SMS | Reduce riesgo de bloqueo |
| **Delay entre mensajes** | 2-3 segundos | Evita throttling automático |
| **Pausa entre lotes** | Manual | Permite verificar resultados |

## 📊 Estados de Mensaje

- **Pendiente** - En espera de envío
- **Enviando...** - Procesando envío
- **Enviado** - SMS enviado exitosamente
- **Entregado** - Confirmado por operadora
- **Error: [Detalle]** - Falló con razón específica

## 🛠️ Desarrollo

### Tecnologías
- **Kotlin** - Lenguaje principal
- **Jetpack Compose** - UI moderna
- **Material Design 3** - Diseño
- **SMS Manager API** - Envío de mensajes
- **Coroutines** - Operaciones asíncronas

### Arquitectura
```
app/
├── MainActivity.kt          # Actividad principal
├── BroadcastReceivers      # Verificación SMS
├── CSV Parser              # Procesamiento archivos
├── SMS Manager             # Envío por lotes
└── Report Generator        # Exportación CSV
```

### Compilar
```bash
# Debug
./gradlew assembleDebug

# Release firmado
./gradlew assembleRelease
```

## 📱 Compatibilidad

- **Android 12+** (API 31)
- **RAM**: 2GB mínimo
- **Almacenamiento**: 50MB
- **Permisos**: SMS, Archivos

## ⚖️ Consideraciones Legales

### ⚠️ Importante
- Obtén **consentimiento explícito** antes de enviar SMS
- Respeta leyes **anti-spam** locales
- Incluye instrucciones de **opt-out**
- Verifica regulaciones de tu país/región

### Responsabilidad
- **El usuario es responsable** del cumplimiento legal
- Consulta abogado para uso comercial
- Respeta términos de servicio de operadoras

## 🐛 Solución de Problemas

### Errores Comunes

**"Sin servicio"**
- Verificar cobertura de red
- Probar con un SMS manual

**"Radio apagado"**
- Habilitar datos móviles
- Verificar modo avión desactivado

**"Archivo no válido"**
- Verificar extensión `.csv`
- Comprobar codificación UTF-8

### Logs
```bash
adb logcat | grep SMS
```

## 🤝 Contribuir

1. Fork el proyecto
2. Crea una rama (`git checkout -b feature/nueva-funcionalidad`)
3. Commit cambios (`git commit -am 'Agrega nueva funcionalidad'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

### Desarrollo Local
```bash
# Clonar
git clone https://github.com/tu-usuario/sms-masivo-app.git

# Instalar dependencias
./gradlew build

# Ejecutar tests
./gradlew test
```

## 📄 Licencia

```
Copyright 2025 Raúl Risco Castillo

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 👨‍💻 Autor

**Raúl Risco Castillo**
- GitHub: [@rriscomba](https://github.com/rriscomba)

## 🏷️ Changelog

### v1.0.0 (2025-01-08)
- ✨ Envío masivo de SMS desde CSV
- ✅ Verificación de entrega real
- 📊 Reportes exportables
- ⚙️ Configuración de lotes
- 🎨 Interfaz Material Design

---

⭐ **¡Dale una estrella si te resulta útil
