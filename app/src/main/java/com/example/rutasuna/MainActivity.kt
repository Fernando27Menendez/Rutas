package com.example.rutasuna
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.*
import kotlinx.coroutines.launch
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.geojson.LineString
import androidx.compose.runtime.mutableStateOf

data class RouteNode(
    val id: String,
    val name: String,
    val point: Point,
    val type: NodeType,
    val description: String = ""
)

/**
 * Tipos de nodos en el campus
 */
enum class NodeType {
    ENTRANCE,    // Puertas de entrada
    DESTINATION, // Destinos principales
    WAYPOINT     // Puntos intermedios en rutas
}

/**
 * Representa una conexión entre dos nodos
 */
data class RouteEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val weight: Double, // Peso de la conexión (distancia en metros)
    val waypoints: List<Point> = emptyList() // Puntos intermedios para la ruta visual
)

data class RouteResult(
    val nodes: List<RouteNode>,
    val totalDistance: Double,
    val waypoints: List<Point>,
    val success: Boolean,
    val errorMessage: String = "",
    val routeDescription: String = "" // Descripción de la ruta para debug
)

fun calculateDistance(point1: Point, point2: Point): Double
{
    val r = 6371000.0 // Radio de la Tierra en metros

    val lat1Rad = Math.toRadians(point1.latitude())
    val lat2Rad = Math.toRadians(point2.latitude())
    val deltaLatRad = Math.toRadians(point2.latitude() - point1.latitude())
    val deltaLngRad = Math.toRadians(point2.longitude() - point1.longitude())

    val a = sin(deltaLatRad / 2).pow(2) +
            cos(lat1Rad) * cos(lat2Rad) * sin(deltaLngRad / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return r * c
}

class CampusGraph {

    // Definición de todos los nodos del campus
    private val nodes = mapOf(
        // === NODOS DE ENTRADA ===
        "puerta_principal" to RouteNode(
            id = "puerta_principal",
            name = "Puerta Principal",
            point = Point.fromLngLat(-70.016496, -15.827654),
            type = NodeType.ENTRANCE,
            description = "Entrada principal del campus"
        ),
        "puerta_postgrado" to RouteNode(
            id = "puerta_postgrado",
            name = "Puerta PostGrado",
            point = Point.fromLngLat(-70.013611, -15.826618),
            type = NodeType.ENTRANCE,
            description = "Entrada por postgrado"
        ),
        "puerta_ingenierias" to RouteNode(
            id = "puerta_ingenierias",
            name = "Puerta de Ingenierías",
            point = Point.fromLngLat(-70.019388, -15.823875),
            type = NodeType.ENTRANCE,
            description = "Entrada por facultad de ingenierías"
        ),

        // === NODOS DE DESTINO ===
        "plaza_central" to RouteNode(
            id = "plaza_central",
            name = "Plaza Central",
            point = Point.fromLngLat(-70.016186, -15.824301),
            type = NodeType.DESTINATION,
            description = "Plaza central del campus"
        ),
        "biblioteca_central" to RouteNode(
            id = "biblioteca_central",
            name = "Biblioteca Central",
            point = Point.fromLngLat(-70.015944, -15.823725),
            type = NodeType.DESTINATION,
            description = "Biblioteca central universitaria"
        ),
        "sistemas_antiguo" to RouteNode(
            id = "sistemas_antiguo",
            name = "E.P. Sistemas Pabellón Antiguo",
            point = Point.fromLngLat(-70.017768, -15.823674),
            type = NodeType.DESTINATION,
            description = "Escuela de Sistemas - Pabellón Antiguo"
        ),
        "sistemas_nuevo" to RouteNode(
            id = "sistemas_nuevo",
            name = "E.P. Sistemas Pabellón Nuevo",
            point = Point.fromLngLat(-70.017794, -15.823510),
            type = NodeType.DESTINATION,
            description = "Escuela de Sistemas - Pabellón Nuevo"
        ),

        // === NODOS INTERMEDIOS (WAYPOINTS) ===
        // Estos nodos conectan las rutas y siguen los caminos reales
        "interseccion_central" to RouteNode(
            id = "interseccion_central",
            name = "Intersección Central",
            point = Point.fromLngLat(-70.016800, -15.824500),
            type = NodeType.WAYPOINT,
            description = "Intersección principal de caminos"
        ),
        "camino_biblioteca" to RouteNode(
            id = "camino_biblioteca",
            name = "Camino a Biblioteca",
            point = Point.fromLngLat(-70.016200, -15.823900),
            type = NodeType.WAYPOINT,
            description = "Punto intermedio hacia biblioteca"
        ),
        "camino_sistemas" to RouteNode(
            id = "camino_sistemas",
            name = "Camino a Sistemas",
            point = Point.fromLngLat(-70.017200, -15.823800),
            type = NodeType.WAYPOINT,
            description = "Punto intermedio hacia sistemas"
        )
    )

    private val edges = listOf(
        // === DESDE PUERTA PRINCIPAL ===
        RouteEdge("puerta_principal", "interseccion_central", 340.0),
        RouteEdge("interseccion_central", "puerta_principal", 340.0),

        // === DESDE PUERTA POSTGRADO ===
        RouteEdge("puerta_postgrado", "biblioteca_central", 280.0),
        RouteEdge("biblioteca_central", "puerta_postgrado", 280.0),
        RouteEdge("puerta_postgrado", "camino_biblioteca", 200.0),
        RouteEdge("camino_biblioteca", "puerta_postgrado", 200.0),

        // === DESDE PUERTA INGENIERIAS ===
        RouteEdge("puerta_ingenierias", "camino_sistemas", 150.0),
        RouteEdge("camino_sistemas", "puerta_ingenierias", 150.0),
        RouteEdge("puerta_ingenierias", "sistemas_antiguo", 200.0),
        RouteEdge("sistemas_antiguo", "puerta_ingenierias", 200.0),

        // === CONEXIONES INTERNAS ===
        RouteEdge("interseccion_central", "plaza_central", 180.0),
        RouteEdge("plaza_central", "interseccion_central", 180.0),

        RouteEdge("interseccion_central", "camino_biblioteca", 120.0),
        RouteEdge("camino_biblioteca", "interseccion_central", 120.0),

        RouteEdge("camino_biblioteca", "biblioteca_central", 100.0),
        RouteEdge("biblioteca_central", "camino_biblioteca", 100.0),

        RouteEdge("interseccion_central", "camino_sistemas", 160.0),
        RouteEdge("camino_sistemas", "interseccion_central", 160.0),

        RouteEdge("camino_sistemas", "sistemas_antiguo", 80.0),
        RouteEdge("sistemas_antiguo", "camino_sistemas", 80.0),

        RouteEdge("camino_sistemas", "sistemas_nuevo", 90.0),
        RouteEdge("sistemas_nuevo", "camino_sistemas", 90.0),

        RouteEdge("sistemas_antiguo", "sistemas_nuevo", 20.0),
        RouteEdge("sistemas_nuevo", "sistemas_antiguo", 20.0),

        // === CONEXIONES DIRECTAS ENTRE DESTINOS ===
        RouteEdge("plaza_central", "biblioteca_central", 150.0),
        RouteEdge("biblioteca_central", "plaza_central", 150.0),

        RouteEdge("plaza_central", "camino_sistemas", 220.0),
        RouteEdge("camino_sistemas", "plaza_central", 220.0),
    )

    // Crear un mapa de adyacencias para búsqueda eficiente
    private val adjacencyMap: Map<String, List<RouteEdge>> by lazy {
        edges.groupBy { it.fromNodeId }
    }

    /**
     * Obtiene todos los nodos
     */

    /**
     * Obtiene todos los nodos de entrada (puertas)
     */
    private fun getEntranceNodes(): List<RouteNode> {
        return nodes.values.filter { it.type == NodeType.ENTRANCE }
    }

    /**
     * Obtiene todos los nodos de destino
     */
    fun getDestinationNodes(): List<RouteNode> {
        return nodes.values.filter { it.type == NodeType.DESTINATION }
    }

    /**
     * Obtiene un nodo por su ID
     */
    private fun getNode(nodeId: String): RouteNode? {
        return nodes[nodeId]
    }

    /**
     * Obtiene las conexiones desde un nodo específico
     */
    private fun getEdgesFrom(nodeId: String): List<RouteEdge> {
        return adjacencyMap[nodeId] ?: emptyList()
    }

    /**
     * Encuentra el nodo de entrada más cercano a una coordenada
     */
    fun findNearestEntrance(userLocation: Point): RouteNode? {
        return getEntranceNodes()
            .minByOrNull { node ->
                calculateDistance(userLocation, node.point)
            }
    }

    /**
     * Encuentra el nodo más cercano a una coordenada (cualquier tipo)
     * CORREGIDO: Ahora considera la lógica de navegación
     */
    fun findNearestNode(userLocation: Point): RouteNode? {
        return nodes.values
            .minByOrNull { node ->
                calculateDistance(userLocation, node.point)
            }
    }

    /**
     * Encuentra la mejor entrada para llegar a un destino específico
     * NUEVA FUNCIÓN: Usa Dijkstra para determinar la entrada óptima
     */
    fun findBestEntranceForDestination(userLocation: Point, destinationId: String): RouteNode? {
        val entrances = getEntranceNodes()
        var bestEntrance: RouteNode? = null
        var bestTotalDistance = Double.MAX_VALUE

        for (entrance in entrances) {
            // Calcular distancia del usuario a la entrada
            val distanceToEntrance = calculateDistance(userLocation, entrance.point)

            // Calcular distancia de la entrada al destino usando Dijkstra
            val routeFromEntrance = calculateShortestPath(entrance.id, destinationId)

            if (routeFromEntrance.success) {
                val totalDistance = distanceToEntrance + routeFromEntrance.totalDistance

                if (totalDistance < bestTotalDistance) {
                    bestTotalDistance = totalDistance
                    bestEntrance = entrance
                }
            }
        }

        return bestEntrance
    }

    /**
     * Implementación de Dijkstra para encontrar el camino más corto
     * CORREGIDA: Ahora maneja correctamente todos los nodos
     */
    fun calculateShortestPath(startNodeId: String, endNodeId: String): RouteResult {
        val startNode = getNode(startNodeId)
        val endNode = getNode(endNodeId)

        if (startNode == null || endNode == null) {
            return RouteResult(
                nodes = emptyList(),
                totalDistance = 0.0,
                waypoints = emptyList(),
                success = false,
                errorMessage = "Nodo de inicio o destino no encontrado"
            )
        }

        // Implementación de Dijkstra CORREGIDA
        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String?>()
        val unvisited = mutableSetOf<String>()

        // Inicializar TODOS los nodos del grafo
        nodes.keys.forEach { nodeId ->
            distances[nodeId] = if (nodeId == startNodeId) 0.0 else Double.MAX_VALUE
            previous[nodeId] = null
            unvisited.add(nodeId)
        }

        while (unvisited.isNotEmpty()) {
            // Encontrar el nodo no visitado con menor distancia
            val currentNodeId = unvisited.minByOrNull { distances[it] ?: Double.MAX_VALUE }
            if (currentNodeId == null || distances[currentNodeId] == Double.MAX_VALUE) {
                break
            }

            unvisited.remove(currentNodeId)

            // Si llegamos al destino, podemos terminar
            if (currentNodeId == endNodeId) {
                break
            }

            // Revisar todos los vecinos
            getEdgesFrom(currentNodeId).forEach { edge ->
                if (edge.toNodeId in unvisited) {
                    val newDistance = distances[currentNodeId]!! + edge.weight
                    if (newDistance < (distances[edge.toNodeId] ?: Double.MAX_VALUE)) {
                        distances[edge.toNodeId] = newDistance
                        previous[edge.toNodeId] = currentNodeId
                    }
                }
            }
        }

        // Reconstruir el camino
        val path = mutableListOf<String>()
        var currentId: String? = endNodeId

        while (currentId != null) {
            path.add(0, currentId)
            currentId = previous[currentId]
        }

        // Verificar si encontramos una ruta válida
        if (path.isEmpty() || path[0] != startNodeId) {
            return RouteResult(
                nodes = emptyList(),
                totalDistance = 0.0,
                waypoints = emptyList(),
                success = false,
                errorMessage = "No se encontró una ruta entre los puntos seleccionados"
            )
        }

        // Convertir IDs a nodos
        val routeNodes = path.mapNotNull { getNode(it) }
        val totalDistance = distances[endNodeId] ?: 0.0

        // Crear waypoints para la visualización
        val waypoints = routeNodes.map { it.point }

        // Crear descripción de la ruta para debug
        val routeDescription = "Ruta: ${routeNodes.joinToString(" → ") { it.name }}"

        return RouteResult(
            nodes = routeNodes,
            totalDistance = totalDistance,
            waypoints = waypoints,
            success = true,
            routeDescription = routeDescription
        )
    }
}

class RouteCalculator(private val graph: CampusGraph) {


    private fun calculateRoute(startNodeId: String, endNodeId: String): RouteResult {
        return graph.calculateShortestPath(startNodeId, endNodeId)
    }


    fun calculateRouteFromCurrentLocation(
        currentLocation: Point,
        destinationNodeId: String
    ): RouteResult {
        println("🔍 Calculando ruta desde ubicación actual a $destinationNodeId")

        val nearestNode = graph.findNearestNode(currentLocation)
            ?: return RouteResult(
                nodes = emptyList(),
                totalDistance = 0.0,
                waypoints = emptyList(),
                success = false,
                errorMessage = "No se encontró un punto de referencia cercano"
            )

        val distanceToNearestNode = calculateDistance(currentLocation, nearestNode.point)
        println("📍 Nodo más cercano: ${nearestNode.name} (${distanceToNearestNode.toInt()}m)")

        // Si el usuario está muy cerca de un nodo (menos de 50m), usar ese nodo
        if (distanceToNearestNode < 50.0) {
            println("✅ Usuario cerca del nodo ${nearestNode.name}, calculando ruta directa")
            val result = calculateRoute(nearestNode.id, destinationNodeId)

            if (result.success) {
                val fullWaypoints = mutableListOf(currentLocation)
                fullWaypoints.addAll(result.waypoints)

                return result.copy(
                    waypoints = fullWaypoints,
                    totalDistance = result.totalDistance + distanceToNearestNode,
                    routeDescription = "Desde tu ubicación → ${result.routeDescription}"
                )
            }
            return result
        }

        // Si el usuario está lejos (más de 100m del campus), usar la mejor entrada
        val nearestEntrance = graph.findNearestEntrance(currentLocation)
        if (nearestEntrance != null) {
            val distanceToEntrance = calculateDistance(currentLocation, nearestEntrance.point)

            // Si está muy lejos del campus, sugerir ir a la entrada más cercana
            if (distanceToNearestNode > 100.0) {
                println("🚪 Usuario lejos del campus, usando entrada: ${nearestEntrance.name}")
                val result = calculateRoute(nearestEntrance.id, destinationNodeId)

                if (result.success) {
                    return result.copy(
                        routeDescription = "Ve a ${nearestEntrance.name} → ${result.routeDescription}",
                        totalDistance = result.totalDistance + distanceToEntrance
                    )
                }
            }
        }

        // Para usuarios dentro del campus pero no muy cerca de un nodo específico
        // Buscar la mejor entrada para el destino
        val bestEntrance = graph.findBestEntranceForDestination(currentLocation, destinationNodeId)
        if (bestEntrance != null) {
            println("🎯 Mejor entrada para el destino: ${bestEntrance.name}")
            val result = calculateRoute(bestEntrance.id, destinationNodeId)

            if (result.success) {
                val distanceToBestEntrance = calculateDistance(currentLocation, bestEntrance.point)
                return result.copy(
                    routeDescription = "Ve a ${bestEntrance.name} → ${result.routeDescription}",
                    totalDistance = result.totalDistance + distanceToBestEntrance
                )
            }
        }

        return RouteResult(
            nodes = emptyList(),
            totalDistance = 0.0,
            waypoints = emptyList(),
            success = false,
            errorMessage = "No se pudo calcular una ruta óptima"
        )
    }

    /**
     * Calcula ruta desde la entrada más cercana hasta un destino
     * CORREGIDA: Ahora usa la mejor entrada para el destino específico
     */
    fun calculateRouteFromNearestEntrance(
        userLocation: Point,
        destinationNodeId: String
    ): RouteResult {
        println("🚪 Calculando ruta desde mejor entrada a $destinationNodeId")

        val bestEntrance = graph.findBestEntranceForDestination(userLocation, destinationNodeId)
            ?: return RouteResult(
                nodes = emptyList(),
                totalDistance = 0.0,
                waypoints = emptyList(),
                success = false,
                errorMessage = "No se encontró una entrada óptima"
            )

        println("🎯 Mejor entrada seleccionada: ${bestEntrance.name}")
        val result = calculateRoute(bestEntrance.id, destinationNodeId)

        return if (result.success) {
            result.copy(
                routeDescription = "Desde ${bestEntrance.name} → ${result.routeDescription}"
            )
        } else {
            result
        }
    }
}

/**
 * Clase simple para visualizar rutas
 */
class SimpleRouteVisualizer {

    fun showRoute(
        mapView: com.mapbox.maps.MapView,
        routeResult: RouteResult
    ) {
        try {
            // Limpiar rutas anteriores
            mapView.annotations.cleanup()

            if (routeResult.success && routeResult.waypoints.isNotEmpty()) {
                // Crear línea de ruta
                val lineString = LineString.fromLngLats(routeResult.waypoints)
                val polylineManager = mapView.annotations.createPolylineAnnotationManager()

                val polylineOptions = PolylineAnnotationOptions()
                    .withPoints(lineString.coordinates())
                    .withLineColor("#FF4444")
                    .withLineWidth(5.0)
                    .withLineOpacity(0.8)

                polylineManager.create(polylineOptions)

                // Crear marcadores
                val pointManager = mapView.annotations.createPointAnnotationManager()
                routeResult.nodes.forEachIndexed { index, node ->
                    val isFirstOrLast = index == 0 || index == routeResult.nodes.size - 1

                    val markerOptions = PointAnnotationOptions()
                        .withPoint(node.point)
                        .withIconImage(if (isFirstOrLast) "marker-start-end" else "marker-intermediate")
                        .withIconSize(if (isFirstOrLast) 1.2 else 0.8)
                        .withTextField(node.name)
                        .withTextSize(12.0)
                        .withTextColor("#FFFFFF")
                        .withTextHaloColor("#000000")
                        .withTextHaloWidth(2.0)

                    pointManager.create(markerOptions)
                }

                // Log para debug
                println("✅ Ruta visualizada: ${routeResult.routeDescription}")
                println("📏 Distancia total: ${routeResult.totalDistance.toInt()}m")
            }
        } catch (e: Exception) {
            println("❌ Error mostrando ruta: ${e.message}")
        }
    }
}

/**
 * Diálogo de búsqueda simple
 */
@Composable
fun SimpleSearchDialog(
    campusGraph: CampusGraph,
    onDismiss: () -> Unit,
    onRouteFromHere: (String, String) -> Unit,
    onRouteFromEntrance: (String, String) -> Unit
) {
    var selectedId by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val destinations = campusGraph.getDestinationNodes().map { node ->
        node.id to node.name
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🗺️ Buscar Ruta") },
        text = {
            Column{
                Text("Selecciona destino:")

                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedName.ifEmpty { "Seleccionar destino" },
                            modifier = Modifier.weight(1f)
                        )
                        Text("▼")
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        destinations.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedId = id
                                    selectedName = name
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedName.isNotEmpty()) {
                    Card {
                        Text(
                            text = "Destino: $selectedName",
                            modifier = Modifier.padding(12.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        if (selectedId.isNotEmpty()) {
                            onRouteFromHere(selectedId, selectedName)
                        }
                    },
                    enabled = selectedId.isNotEmpty()
                ) {
                    Text("Desde aquí")
                }

                TextButton(
                    onClick = {
                        if (selectedId.isNotEmpty()) {
                            onRouteFromEntrance(selectedId, selectedName)
                        }
                    },
                    enabled = selectedId.isNotEmpty()
                ) {
                    Text("Desde entrada")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

class MainActivity : ComponentActivity() {
    class OrientationHandler(context: Context) : SensorEventListener {

        private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        private val accelerometerValues = FloatArray(3)
        private val magnetometerValues = FloatArray(3)
        private val rotationMatrix = FloatArray(9)
        private val orientationValues = FloatArray(3)

        private var currentBearing = 0f
        private var onBearingChanged: ((Float) -> Unit)? = null
        private var isActive = false

        fun setOnBearingChangedListener(listener: (Float) -> Unit) {
            onBearingChanged = listener
        }

        fun startListening() {
            if (!isActive) {
                isActive = true
                accelerometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
                magnetometer?.let {
                    sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
            }
        }

        fun stopListening() {
            if (isActive) {
                isActive = false
                sensorManager.unregisterListener(this)
            }
        }

        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                when (it.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(it.values, 0, accelerometerValues, 0, it.values.size)
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(it.values, 0, magnetometerValues, 0, it.values.size)
                    }
                }

                updateOrientation()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // No necesitamos manejar cambios de precisión para este caso
        }

        private fun updateOrientation() {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValues, magnetometerValues)) {
                SensorManager.getOrientation(rotationMatrix, orientationValues)

                // Convertir de radianes a grados y normalizar
                var bearing = Math.toDegrees(orientationValues[0].toDouble()).toFloat()

                // Normalizar el bearing entre 0 y 360 grados
                bearing = (bearing + 360) % 360

                // Aplicar suavizado para evitar saltos bruscos
                val smoothedBearing = smoothBearing(bearing)

                if (abs(smoothedBearing - currentBearing) > 2f) { // Solo actualizar si hay cambio significativo
                    currentBearing = smoothedBearing
                    onBearingChanged?.invoke(currentBearing)
                }
            }
        }

        private fun smoothBearing(newBearing: Float): Float {
            val diff = ((newBearing - currentBearing + 540) % 360) - 180
            return currentBearing + diff * 0.1f // Factor de suavizado
        }
    }

    // Clase para manejar la obtención de ubicación
    class LocationProvider(
        private val context: Context,
        private val onLocationReceived: (Location) -> Unit,
        private val onLocationError: (String) -> Unit
    ) : LocationListener {

        private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        private var isRequestingLocation = false

        @SuppressLint("MissingPermission")
        fun getCurrentLocation() {
            if (!hasLocationPermission()) {
                onLocationError("No se tienen permisos de ubicación")
                return
            }

            if (!isLocationEnabled()) {
                onLocationError("Los servicios de ubicación están desactivados")
                return
            }

            if (isRequestingLocation) {
                return // Ya estamos solicitando ubicación
            }

            try {
                isRequestingLocation = true

                // Intentar obtener la última ubicación conocida primero
                val lastKnownGPS = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                } else null

                val lastKnownNetwork = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } else null

                val lastKnownLocation = when {
                    lastKnownGPS != null && lastKnownNetwork != null -> {
                        if (lastKnownGPS.time > lastKnownNetwork.time) lastKnownGPS else lastKnownNetwork
                    }
                    lastKnownGPS != null -> lastKnownGPS
                    lastKnownNetwork != null -> lastKnownNetwork
                    else -> null
                }

                // Si tenemos una ubicación reciente (menos de 2 minutos), la usamos
                if (lastKnownLocation != null && (System.currentTimeMillis() - lastKnownLocation.time) < 120000) {
                    isRequestingLocation = false
                    onLocationReceived(lastKnownLocation)
                    return
                }

                // Si no hay ubicación reciente, solicitar una nueva
                val providers = mutableListOf<String>()
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    providers.add(LocationManager.GPS_PROVIDER)
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    providers.add(LocationManager.NETWORK_PROVIDER)
                }

                if (providers.isEmpty()) {
                    isRequestingLocation = false
                    onLocationError("No hay proveedores de ubicación disponibles")
                    return
                }

                // Solicitar actualizaciones de ubicación
                providers.forEach { provider ->
                    locationManager.requestLocationUpdates(
                        provider,
                        1000L, // minTime
                        1f,    // minDistance
                        this
                    )
                }

                // Timeout después de 10 segundos
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (isRequestingLocation) {
                        stopLocationUpdates()
                        onLocationError("Tiempo de espera agotado para obtener ubicación")
                    }
                }, 10000)

            } catch (e: SecurityException) {
                isRequestingLocation = false
                onLocationError("Error de permisos: ${e.message}")
            } catch (e: Exception) {
                isRequestingLocation = false
                onLocationError("Error al obtener ubicación: ${e.message}")
            }
        }

        override fun onLocationChanged(location: Location) {
            if (isRequestingLocation) {
                stopLocationUpdates()
                onLocationReceived(location)
            }
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in API level 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        @SuppressLint("MissingPermission")
        private fun stopLocationUpdates() {
            try {
                locationManager.removeUpdates(this)
                isRequestingLocation = false
            } catch (e: SecurityException) {
                // Ignorar errores de seguridad al detener actualizaciones
            }
        }

        private fun hasLocationPermission(): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        }

        private fun isLocationEnabled(): Boolean {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }


    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            Toast.makeText(this, "Permisos de ubicación concedidos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Se necesitan permisos de ubicación para mostrar tu posición", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MapScreen() // Función principal que muestra el mapa
        }
    }

    @Composable
    fun MapScreen() {
        var locationEnabled by remember { mutableStateOf(false) }
        var showLocationDialog by remember { mutableStateOf(false) }
        var isLocationServiceEnabled by remember { mutableStateOf(false) }
        var isHighAccuracyEnabled by remember { mutableStateOf(false) }
        var currentBearing by remember { mutableFloatStateOf(0f) }
        var is3DMode by remember { mutableStateOf(true) }
        var showSearchDialog by remember { mutableStateOf(false) }
        var isCenteringLocation by remember { mutableStateOf(false) }
        var isChangingView by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val orientationHandler = remember { OrientationHandler(context) }
        val coroutineScope = rememberCoroutineScope()
        val campusGraph = remember { CampusGraph() }
        val routeCalculator = remember { RouteCalculator(campusGraph) }
        var currentRoute by remember { mutableStateOf<RouteResult?>(null) }
        val routeVisualizer = remember { SimpleRouteVisualizer() }

        // Configurar el listener de orientación
        LaunchedEffect(Unit) {
            orientationHandler.setOnBearingChangedListener { bearing ->
                currentBearing = bearing
            }
            orientationHandler.startListening()
        }

        // Limpiar recursos al salir
        DisposableEffect(Unit) {
            onDispose {
                orientationHandler.stopListening()
            }
        }

        // Verificar permisos y configuración al inicializar
        LaunchedEffect(Unit) {
            val hasLocationPermission = hasLocationPermission()
            if (hasLocationPermission) {
                locationEnabled = true
                isLocationServiceEnabled = isLocationEnabled()
                isHighAccuracyEnabled = isHighAccuracyLocationEnabled()

                if (!isLocationServiceEnabled || !isHighAccuracyEnabled) {
                    showLocationDialog = true
                }
            } else {
                requestLocationPermissions()
            }
        }
        val mapViewportState = rememberMapViewportState {
            setCameraOptions {
                zoom(16.0)
                center(Point.fromLngLat(-70.017242, -15.824642))
                pitch(if (is3DMode) 45.0 else 0.0)
                bearing(0.0)
            }
        }
        // Función para centrar en la ubicación actual CON ANIMACIÓN
        val centerOnCurrentLocation: () -> Unit = {
            if (!locationEnabled) {
                Toast.makeText(context, "Permisos de ubicación no concedidos", Toast.LENGTH_SHORT).show()
            }
            isCenteringLocation = true

            val locationProvider = LocationProvider(
                context = context,
                onLocationReceived = { location ->
                    coroutineScope.launch {
                        try {
                            // Animación suave al centrar ubicación
                            mapViewportState.flyTo(
                                cameraOptions = CameraOptions.Builder()
                                    .center(Point.fromLngLat(location.longitude, location.latitude))
                                    .zoom(18.0)
                                    .pitch(if (is3DMode) 45.0 else 0.0)
                                    .bearing(if (is3DMode) currentBearing.toDouble() else 0.0)
                                    .build(),
                                animationOptions = MapAnimationOptions.Builder()
                                    .duration(1500L) // 1.5 segundos de animación
                                    .build()
                            )

                            Toast.makeText(context, "📍 Ubicación centrada", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error al centrar ubicación: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            // Esperar un poco más para que termine la animación antes de ocultar el indicador
                            kotlinx.coroutines.delay(1600L)
                            isCenteringLocation = false
                        }
                    }
                },
                onLocationError = { error ->
                    isCenteringLocation = false
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            )

            locationProvider.getCurrentLocation()
        }

        // Función para cambiar vista 2D/3D CON ANIMACIÓN
        val toggleViewMode: () -> Unit = {
            isChangingView = true
            is3DMode = !is3DMode

            coroutineScope.launch {
                try {
                    // Obtener la cámara actual
                    val currentCamera = mapViewportState.cameraState

                    // Animación suave para el cambio de vista
                    if (currentCamera != null) {
                        mapViewportState.flyTo(
                            cameraOptions = CameraOptions.Builder()
                                .center(currentCamera.center)
                                .zoom(currentCamera.zoom)
                                .pitch(if (is3DMode) 60.0 else 0.0)
                                .bearing(if (is3DMode) currentBearing.toDouble() else 0.0)
                                .build(),
                            animationOptions = MapAnimationOptions.Builder()
                                .duration(800L) // 0.8 segundos de animación
                                .build()
                        )
                    }

                    // Esperar a que termine la animación
                    kotlinx.coroutines.delay(900L)

                } catch (e: Exception) {
                    println("Error en animación de vista: ${e.message}")
                } finally {
                    isChangingView = false
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
            ) {
                MapEffect(locationEnabled, currentBearing, currentRoute) { mapView ->
                    if (locationEnabled) {
                        mapView.location.updateSettings {
                            enabled = true
                            locationPuck = createDefault2DPuck(withBearing = true)
                            puckBearingEnabled = true
                            puckBearing = PuckBearing.HEADING
                            pulsingEnabled = false
                            showAccuracyRing = false
                        }
                    }

                    // Mostrar ruta si existe
                    currentRoute?.let { route ->
                        routeVisualizer.showRoute(mapView, route)
                    }
                }
            }

            // Botones de control en la esquina superior derecha
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón para centrar ubicación - CON ANIMACIÓN MEJORADA
                FloatingActionButton(
                    onClick = centerOnCurrentLocation,
                    modifier = Modifier.size(56.dp), // Tamaño más grande para mejor UX
                    containerColor = if (isCenteringLocation)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.primary
                ) {
                    if (isCenteringLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Text(
                            "📍",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                // Botón para cambiar vista 2D/3D - CON ANIMACIÓN MEJORADA
                FloatingActionButton(
                    onClick = toggleViewMode,
                    modifier = Modifier.size(56.dp),
                    containerColor = if (isChangingView)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.secondary
                ) {
                    if (isChangingView) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (is3DMode) "3D" else "2D",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                            Text(
                                text = if (is3DMode) "🏗️" else "🗺️",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Botón de búsqueda/rutas
                FloatingActionButton(
                    onClick = {
                        showSearchDialog = true
                    },
                    modifier = Modifier.size(56.dp),
                    containerColor = MaterialTheme.colorScheme.tertiary
                ) {
                    Text("🔍", style = MaterialTheme.typography.titleLarge)
                }
            }

            // Indicador de estado en la parte inferior (opcional)
            if (isCenteringLocation || isChangingView) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = when {
                                isCenteringLocation -> "Centrando ubicación..."
                                isChangingView -> "Cambiando vista..."
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Diálogos
        if (showLocationDialog) {
            LocationSettingsDialog(
                isLocationEnabled = isLocationServiceEnabled,
                isHighAccuracyEnabled = isHighAccuracyEnabled,
                onOpenSettings = {
                    openLocationSettings()
                    showLocationDialog = false
                },
                onDismiss = {
                    showLocationDialog = false
                }
            )
        }

        // Reemplaza showSearchDialog en MapScreen()
        if (showSearchDialog) {
            SimpleSearchDialog(
                campusGraph = campusGraph,
                onDismiss = { showSearchDialog = false },
                onRouteFromHere = { destinationId, destinationName ->
                    // Calcular ruta desde ubicación actual
                    val locationProvider = LocationProvider(
                        context = context,
                        onLocationReceived = { location ->
                            val currentPoint = Point.fromLngLat(location.longitude, location.latitude)
                            val routeResult = routeCalculator.calculateRouteFromCurrentLocation(
                                currentPoint,
                                destinationId
                            )

                            if (routeResult.success) {
                                currentRoute = routeResult
                                Toast.makeText(
                                    context,
                                    "Ruta calculada: ${routeResult.totalDistance.toInt()}m a $destinationName",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, routeResult.errorMessage, Toast.LENGTH_LONG).show()
                            }
                        },
                        onLocationError = { error ->
                            Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                        }
                    )
                    locationProvider.getCurrentLocation()
                    showSearchDialog = false
                },
                onRouteFromEntrance = { destinationId, destinationName ->
                    // Calcular ruta desde entrada más cercana
                    val locationProvider = LocationProvider(
                        context = context,
                        onLocationReceived = { location ->
                            val currentPoint = Point.fromLngLat(location.longitude, location.latitude)
                            val routeResult = routeCalculator.calculateRouteFromNearestEntrance(
                                currentPoint,
                                destinationId
                            )

                            if (routeResult.success) {
                                currentRoute = routeResult
                                Toast.makeText(
                                    context,
                                    "Ruta desde entrada: ${routeResult.totalDistance.toInt()}m a $destinationName",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, routeResult.errorMessage, Toast.LENGTH_LONG).show()
                            }
                        },
                        onLocationError = { error ->
                            Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                        }
                    )
                    locationProvider.getCurrentLocation()
                    showSearchDialog = false
                }
            )
        }
    }

    @Composable
    private fun LocationSettingsDialog(
        isLocationEnabled: Boolean,
        isHighAccuracyEnabled: Boolean,
        onOpenSettings: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Configuración de Ubicación",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column {
                    when {
                        !isLocationEnabled -> {
                            Text(
                                text = "La ubicación está desactivada. Para una mejor experiencia, activa la ubicación en tu dispositivo.",
                                textAlign = TextAlign.Center
                            )
                        }
                        !isHighAccuracyEnabled -> {
                            Text(
                                text = "Para obtener una ubicación más precisa y estable, te recomendamos activar la 'Ubicación de Alta Precisión' que usa GPS, Wi-Fi y redes móviles.",
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "💡 Consejo:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "La alta precisión mejora significativamente la estabilidad de tu ubicación en el mapa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onOpenSettings) {
                    Text("Ir a Configuración")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Continuar")
                }
            }
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun isHighAccuracyLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }
}