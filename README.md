# JCamara
Aplicación Java nativa para el manejo y captura en tiempo real de cámaras TT-Mini Spy, WF-A9-V3 y similares. <br>
Basado en el proyecto: https://github.com/DEEFRAG/A9.git<br>

# Objetivos alcanzados:
* Cambiar estado del modo AP al modo estación (STA).
* Detección de cámaras por medio de ARP (Windows, GNU/Linux y MacOS).
* Captura de imagen en tiempo real mientras está en modo AP.
* Guardar imágenes.
* Captura de video.

# TODO
* GUI
* Manejar múltiples cámaras en tiempo real.
* Transmisión en tiempo real durante el modo STA.

# Notas importantes:
Durante el análisis de los datos enviados por UDP desde la aplicación para Android Little_Starts, se detectaron conexiones a servidores en China y Amazon AWS EC2. Entre los datos que se envían se encuentra información sensible relativa a la API de la cámara (incluída contraseña en texto plano). Si el uso que dará a estas cámaras no es de importancia, no se preocupe y continue su uso; advierto, son extremadamente inseguras estas cámaras, úselas bajo su propio riesgo.

# Capturas de la aplicación:
<img width="893" height="781" alt="imagen" src="https://github.com/user-attachments/assets/bbaa7a30-d35d-466c-bbe0-e2268da36a79" />


