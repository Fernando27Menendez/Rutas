# 🗺️ Android Maps Navigation with Dijkstra

Una aplicación Android que implementa navegación inteligente usando el algoritmo de Dijkstra para encontrar rutas óptimas, desarrollada con Kotlin, Jetpack Compose y MapBox SDK.

## 📱 Características

- **Navegación Inteligente**: Implementación del algoritmo de Dijkstra para cálculo de rutas óptimas
- **Interfaz Moderna**: Desarrollada completamente con Jetpack Compose
- **Mapas Interactivos**: Integración con MapBox SDK para visualización de mapas
- **Rutas Personalizadas**: Puntos de ruta definidos manualmente con coordenadas de longitud y latitud
- **Waypoints**: Sistema de waypoints para mejorar la precisión de las rutas
- **Visualización en Tiempo Real**: Renderizado de rutas con puntos de referencia personalizados

## 🛠️ Tecnologías Utilizadas

- **Kotlin** - Lenguaje de programación principal
- **Jetpack Compose** - Framework de UI moderno para Android
- **MapBox SDK** - Servicios de mapas y navegación
- **Android Studio** - Entorno de desarrollo integrado

## 🚀 Instalación

### Requisitos Previos

- Android Studio Arctic Fox o superior
- SDK de Android 21+
- Token de acceso de MapBox

### Configuración

1. **Clona el repositorio**
   ```bash
   git clone https://github.com/tu-usuario/tu-repositorio.git
   cd tu-repositorio
   ```

2. **Configura MapBox**
   - Obtén tu token de acceso en [MapBox](https://www.mapbox.com/)
   - Agrega tu token en `local.properties`:
   ```properties
   MAPBOX_ACCESS_TOKEN=tu_token_aqui
   ```

3. **Sincroniza el proyecto**
   - Abre el proyecto en Android Studio
   - Espera a que se sincronicen las dependencias

4. **Ejecuta la aplicación**
   - Conecta un dispositivo Android o usa el emulador
   - Presiona Run en Android Studio

## 🏗️ Arquitectura

### Algoritmo de Dijkstra
La aplicación implementa el algoritmo de Dijkstra para:
- Calcular la ruta más corta entre dos puntos
- Optimizar el tiempo de viaje considerando diferentes factores
- Manejar múltiples waypoints para rutas complejas

### Estructura del Proyecto
```
app/
├── src/main/java/
│   ├── data/           # Modelos de datos y repositorios
│   ├── ui/             # Componentes de Jetpack Compose
│   ├── navigation/     # Lógica de navegación y algoritmos
│   ├── maps/           # Integración con MapBox
│   └── utils/          # Utilidades y constantes
├── res/
└── AndroidManifest.xml
```

## 🗺️ Funcionalidades

### Definición de Rutas Manuales
- Coordenadas predefinidas con longitud y latitud específicas
- Puntos de referencia estratégicamente ubicados
- Sistema de waypoints para mejorar la precisión del trazado

### Visualización de Mapas
- Integración completa con MapBox SDK
- Renderizado suave de rutas calculadas
- Marcadores personalizados para puntos de interés

### Cálculo de Rutas Óptimas
```kotlin
// Ejemplo de implementación del algoritmo de Dijkstra
class DijkstraPathfinder {
    fun findShortestPath(
        start: LatLng,
        end: LatLng,
        waypoints: List<LatLng>
    ): List<LatLng> {
        // Implementación del algoritmo
    }
}
```

## 📊 Dependencias Principales

```gradle
dependencies {
    // Jetpack Compose
    implementation "androidx.compose.ui:ui:$compose_version"
    implementation "androidx.compose.material3:material3:$material3_version"
    
    // MapBox
    implementation "com.mapbox.maps:android:$mapbox_version"
    
    // Navigation
    implementation "androidx.navigation:navigation-compose:$nav_version"
    
    // Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines_version"
}
```

## 🎯 Uso

1. **Inicia la aplicación**
2. **Selecciona tu ubicación de inicio**
3. **Elige tu destino**
4. **Agrega waypoints opcionales** para personalizar tu ruta
5. **Visualiza la ruta óptima** calculada por el algoritmo de Dijkstra


## 📝 Licencia



## 📞 Contacto

FERNANDO MANUEL MENENDEZ COPARI - [@]() - fmenendezc@est.unap.edu.pe



## 🙏 Reconocimientos

- [MapBox](https://www.mapbox.com/) por su excelente SDK de mapas
- [Jetpack Compose](https://developer.android.com/jetpack/compose) por el framework de UI
- Comunidad de Android por los recursos y documentación

---

⭐ ¡No olvides darle una estrella al proyecto si te resultó útil!
