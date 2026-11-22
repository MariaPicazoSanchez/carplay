package es.maria.rutapanel;

import android.util.JsonReader;
import android.util.JsonToken;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Calcula la ruta en coche más rápida entre dos puntos.
 *
 * <p>Descarga la red de carreteras de OpenStreetMap (Overpass API) alrededor del origen y del
 * destino, construye un grafo dirigido (respeta sentidos únicos y rotondas) y ejecuta A* con
 * coste = tiempo estimado de conducción y heurística = distancia en línea recta a la velocidad
 * máxima permitida. Esa heurística nunca sobrestima, así que A* devuelve el camino óptimo del
 * grafo explorando muchos menos nodos que Dijkstra.
 *
 * <p>Para trayectos largos (más de {@link #LOCAL_LIMIT_KM} km en línea recta), o si la red
 * descargada no conecta ambos puntos, se usa el servidor público OSRM, que también calcula la
 * ruta óptima (Dijkstra multinivel) sobre los mismos datos de OpenStreetMap.
 */
final class RoutePlanner {
    static final double LOCAL_LIMIT_KM = 60;
    private static final double MAX_SPEED = 130 / 3.6;
    /** Velocidad usada para el tramo entre el punto exacto y la carretera más cercana. */
    private static final double ACCESS_SPEED = 20 / 3.6;
    private static final double EARTH_RADIUS = 6_371_008.8;
    private static final int MAX_NODES = 1_500_000;
    private static final String[] OVERPASS = {
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
    };
    private static final String OSRM = "https://router.project-osrm.org/route/v1/driving/";
    private static final String NOMINATIM = "https://nominatim.openstreetmap.org/search";
    private static final String ALL_ROADS = "motorway|trunk|primary|secondary|tertiary|unclassified"
            + "|residential|living_street|road|motorway_link|trunk_link|primary_link|secondary_link"
            + "|tertiary_link";
    private static final String TERTIARY_UP = "motorway|trunk|primary|secondary|tertiary"
            + "|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link";
    private static final String SECONDARY_UP = "motorway|trunk|primary|secondary"
            + "|motorway_link|trunk_link|primary_link|secondary_link";
    private static final String CAR_ALLOWED = "[access!~\"^(no|private)$\"]"
            + "[motor_vehicle!~\"^(no|private)$\"][motorcar!~\"^(no|private)$\"][area!=yes]";

    static final class NoRouteException extends Exception {
        NoRouteException(String message) {
            super(message);
        }
    }

    static final class Place {
        final String label;
        final double lat;
        final double lon;

        Place(String label, double lat, double lon) {
            this.label = label;
            this.lat = lat;
            this.lon = lon;
        }
    }

    static final class Route {
        final double[] lat;
        final double[] lon;
        /** Distancia acumulada desde el inicio hasta cada punto, en metros. */
        final double[] cumulative;
        final double meters;
        final double seconds;
        final String engine;

        Route(double[] lat, double[] lon, double seconds, String engine) {
            this.lat = lat;
            this.lon = lon;
            this.seconds = seconds;
            this.engine = engine;
            cumulative = new double[lat.length];
            for (int i = 1; i < lat.length; i++) {
                cumulative[i] = cumulative[i - 1] + distance(lat[i - 1], lon[i - 1], lat[i], lon[i]);
            }
            meters = cumulative[lat.length - 1];
        }

        /** Devuelve {distancia a la ruta, metros restantes hasta el destino}. */
        double[] locate(double pointLat, double pointLon) {
            double best = Double.MAX_VALUE;
            double remaining = meters;
            double scale = Math.cos(Math.toRadians(pointLat)) * EARTH_RADIUS * Math.PI / 180;
            double latScale = EARTH_RADIUS * Math.PI / 180;
            for (int i = 1; i < lat.length; i++) {
                double ax = (lon[i - 1] - pointLon) * scale, ay = (lat[i - 1] - pointLat) * latScale;
                double bx = (lon[i] - pointLon) * scale, by = (lat[i] - pointLat) * latScale;
                double dx = bx - ax, dy = by - ay;
                double length2 = dx * dx + dy * dy;
                double t = length2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / length2));
                double px = ax + t * dx, py = ay + t * dy;
                double d = Math.sqrt(px * px + py * py);
                if (d < best) {
                    best = d;
                    double segment = cumulative[i] - cumulative[i - 1];
                    remaining = meters - (cumulative[i - 1] + t * segment);
                }
            }
            return new double[]{best, remaining};
        }
    }

    private final String userAgent;

    RoutePlanner(String userAgent) {
        this.userAgent = userAgent;
    }

    Route plan(double fromLat, double fromLon, double toLat, double toLon)
            throws IOException, NoRouteException, InterruptedException {
        double km = distance(fromLat, fromLon, toLat, toLon) / 1000;
        if (km > LOCAL_LIMIT_KM) return osrm(fromLat, fromLon, toLat, toLon);
        try {
            Graph graph = downloadGraph(overpassQuery(fromLat, fromLon, toLat, toLon, km));
            return aStar(graph, fromLat, fromLon, toLat, toLon);
        } catch (IOException | NoRouteException local) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            try {
                return osrm(fromLat, fromLon, toLat, toLon);
            } catch (IOException remote) {
                throw local;
            }
        }
    }

    // ---------------------------------------------------------------- A*

    static Route aStar(Graph graph, double fromLat, double fromLon, double toLat, double toLon)
            throws NoRouteException, InterruptedException {
        int n = graph.size;
        int goal = n;
        int[] sources = graph.nearest(fromLat, fromLon, true);
        int[] targets = graph.nearest(toLat, toLon, false);
        if (sources.length == 0) throw new NoRouteException("No hay carreteras cerca de tu posición.");
        if (targets.length == 0) throw new NoRouteException("No hay carreteras cerca del destino.");
        double[] goalCost = new double[targets.length];
        for (int i = 0; i < targets.length; i++) {
            int t = targets[i];
            goalCost[i] = distance(graph.lat[t], graph.lon[t], toLat, toLon) / ACCESS_SPEED;
        }

        double[] g = new double[n + 1];
        Arrays.fill(g, Double.POSITIVE_INFINITY);
        int[] parent = new int[n + 1];
        Arrays.fill(parent, -1);
        boolean[] closed = new boolean[n + 1];
        int[] targetIndex = new int[n];
        Arrays.fill(targetIndex, -1);
        for (int i = 0; i < targets.length; i++) targetIndex[targets[i]] = i;

        MinHeap open = new MinHeap();
        for (int s : sources) {
            double start = distance(fromLat, fromLon, graph.lat[s], graph.lon[s]) / ACCESS_SPEED;
            if (start < g[s]) {
                g[s] = start;
                open.push(start + heuristic(graph, s, toLat, toLon), s);
            }
        }
        int expanded = 0;
        while (!open.isEmpty()) {
            int u = open.pop();
            if (closed[u]) continue;
            closed[u] = true;
            if (u == goal) break;
            if ((++expanded & 4095) == 0 && Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            int target = targetIndex[u];
            if (target >= 0 && g[u] + goalCost[target] < g[goal]) {
                g[goal] = g[u] + goalCost[target];
                parent[goal] = u;
                open.push(g[goal], goal);
            }
            for (int e = graph.offset[u], end = graph.offset[u + 1]; e < end; e++) {
                int v = graph.target[e];
                if (closed[v]) continue;
                double candidate = g[u] + graph.cost[e];
                if (candidate < g[v]) {
                    g[v] = candidate;
                    parent[v] = u;
                    open.push(candidate + heuristic(graph, v, toLat, toLon), v);
                }
            }
        }
        if (!closed[goal]) throw new NoRouteException("No se encontró una ruta en coche a ese destino.");

        int count = 0;
        for (int v = parent[goal]; v >= 0; v = parent[v]) count++;
        double[] lat = new double[count + 2];
        double[] lon = new double[count + 2];
        lat[0] = fromLat;
        lon[0] = fromLon;
        int i = count;
        for (int v = parent[goal]; v >= 0; v = parent[v], i--) {
            lat[i] = graph.lat[v];
            lon[i] = graph.lon[v];
        }
        lat[count + 1] = toLat;
        lon[count + 1] = toLon;
        return new Route(lat, lon, g[goal], "A*");
    }

    /** Admisible y consistente: ningún tramo se recorre más rápido que {@link #MAX_SPEED}. */
    private static double heuristic(Graph graph, int node, double toLat, double toLon) {
        return distance(graph.lat[node], graph.lon[node], toLat, toLon) / MAX_SPEED;
    }

    // ---------------------------------------------------------------- Grafo

    static final class Graph {
        final int size;
        final double[] lat;
        final double[] lon;
        final int[] offset;
        final int[] target;
        final float[] cost;
        final int[] inDegree;

        Graph(int size, double[] lat, double[] lon, int[] from, int[] to, float[] edgeCost, int edges) {
            this.size = size;
            this.lat = lat;
            this.lon = lon;
            offset = new int[size + 1];
            inDegree = new int[size];
            for (int e = 0; e < edges; e++) {
                offset[from[e] + 1]++;
                inDegree[to[e]]++;
            }
            for (int i = 0; i < size; i++) offset[i + 1] += offset[i];
            target = new int[edges];
            cost = new float[edges];
            int[] next = Arrays.copyOf(offset, size);
            for (int e = 0; e < edges; e++) {
                int slot = next[from[e]]++;
                target[slot] = to[e];
                cost[slot] = edgeCost[e];
            }
        }

        /**
         * Hasta 6 nodos cercanos por los que se puede salir (origen) o llegar (destino). Usar varios
         * evita quedar atrapado en un tramo aislado o en el sentido contrario de una calle.
         */
        int[] nearest(double pointLat, double pointLon, boolean leaving) {
            final int keep = 6;
            int[] best = new int[keep];
            double[] bestDistance = new double[keep];
            Arrays.fill(bestDistance, Double.MAX_VALUE);
            int found = 0;
            for (int i = 0; i < size; i++) {
                boolean usable = leaving ? offset[i + 1] > offset[i] : inDegree[i] > 0;
                if (!usable) continue;
                double d = distance(pointLat, pointLon, lat[i], lon[i]);
                if (d >= bestDistance[keep - 1]) continue;
                int j = Math.min(found, keep - 1);
                while (j > 0 && bestDistance[j - 1] > d) {
                    bestDistance[j] = bestDistance[j - 1];
                    best[j] = best[j - 1];
                    j--;
                }
                bestDistance[j] = d;
                best[j] = i;
                if (found < keep) found++;
            }
            if (found == 0 || bestDistance[0] > 2000) return new int[0];
            int limit = 0;
            while (limit < found && (limit == 0 || bestDistance[limit] <= 300)) limit++;
            return Arrays.copyOf(best, limit);
        }
    }

    static String overpassQuery(double aLat, double aLon, double bLat, double bLon, double km) {
        StringBuilder q = new StringBuilder("[out:json][timeout:60];(");
        if (km <= 8) {
            way(q, ALL_ROADS + "|service", bbox(aLat, aLon, bLat, bLon, Math.max(1.5, km * 0.3)));
        } else {
            // Red completa cerca de los extremos y solo vías principales en el corredor intermedio.
            for (double[] p : new double[][]{{aLat, aLon}, {bLat, bLon}}) {
                way(q, ALL_ROADS, around(3000, p[0], p[1]));
                way(q, "service", around(800, p[0], p[1]));
            }
            way(q, km <= 25 ? TERTIARY_UP : SECONDARY_UP,
                    bbox(aLat, aLon, bLat, bLon, Math.max(5, km * 0.15)));
        }
        // Los nodos primero, para conocer sus coordenadas al leer las vías.
        return q.append(")->.w;node(w.w);out skel qt;.w out body qt;").toString();
    }

    private static void way(StringBuilder q, String classes, String area) {
        q.append("way[\"highway\"~\"^(").append(classes).append(")$\"]").append(CAR_ALLOWED)
                .append(area).append(';');
    }

    private static String bbox(double aLat, double aLon, double bLat, double bLon, double marginKm) {
        double midLat = (aLat + bLat) / 2;
        double dLat = marginKm / 111.32;
        double dLon = marginKm / (111.32 * Math.max(0.1, Math.cos(Math.toRadians(midLat))));
        return String.format(Locale.ROOT, "(%.6f,%.6f,%.6f,%.6f)",
                Math.min(aLat, bLat) - dLat, Math.min(aLon, bLon) - dLon,
                Math.max(aLat, bLat) + dLat, Math.max(aLon, bLon) + dLon);
    }

    private static String around(int meters, double lat, double lon) {
        return String.format(Locale.ROOT, "(around:%d,%.6f,%.6f)", meters, lat, lon);
    }

    private Graph downloadGraph(String query) throws IOException, InterruptedException {
        IOException last = null;
        for (String endpoint : OVERPASS) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            try (InputStream in = request(endpoint, "data=" + URLEncoder.encode(query, "UTF-8"))) {
                return parseGraph(in);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    static Graph parseGraph(InputStream in) throws IOException, InterruptedException {
        LongIntMap ids = new LongIntMap();
        double[] lat = new double[1 << 14];
        double[] lon = new double[1 << 14];
        int nodes = 0;
        int[] from = new int[1 << 15];
        int[] to = new int[1 << 15];
        float[] cost = new float[1 << 15];
        int edges = 0;
        boolean sawElements = false;
        long[] refs = new long[256];

        JsonReader json = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        json.beginObject();
        while (json.hasNext()) {
            if (!json.nextName().equals("elements")) {
                json.skipValue();
                continue;
            }
            sawElements = true;
            json.beginArray();
            while (json.hasNext()) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                String type = null;
                long id = 0;
                double nodeLat = Double.NaN, nodeLon = Double.NaN;
                int refCount = 0;
                String highway = null, oneway = null, junction = null, maxspeed = null;
                json.beginObject();
                while (json.hasNext()) {
                    switch (json.nextName()) {
                        case "type": type = json.nextString(); break;
                        case "id": id = json.nextLong(); break;
                        case "lat": nodeLat = json.nextDouble(); break;
                        case "lon": nodeLon = json.nextDouble(); break;
                        case "nodes":
                            json.beginArray();
                            while (json.hasNext()) {
                                if (refCount == refs.length) refs = Arrays.copyOf(refs, refCount * 2);
                                refs[refCount++] = json.nextLong();
                            }
                            json.endArray();
                            break;
                        case "tags":
                            json.beginObject();
                            while (json.hasNext()) {
                                String key = json.nextName();
                                if (json.peek() != JsonToken.STRING) {
                                    json.skipValue();
                                    continue;
                                }
                                String value = json.nextString();
                                switch (key) {
                                    case "highway": highway = value; break;
                                    case "oneway": oneway = value; break;
                                    case "junction": junction = value; break;
                                    case "maxspeed": maxspeed = value; break;
                                    default: break;
                                }
                            }
                            json.endObject();
                            break;
                        default: json.skipValue();
                    }
                }
                json.endObject();
                if ("node".equals(type) && !Double.isNaN(nodeLat) && !Double.isNaN(nodeLon)) {
                    if (ids.get(id) >= 0) continue;
                    if (nodes == MAX_NODES) throw new IOException("Zona demasiado grande");
                    if (nodes == lat.length) {
                        lat = Arrays.copyOf(lat, nodes * 2);
                        lon = Arrays.copyOf(lon, nodes * 2);
                    }
                    ids.put(id, nodes);
                    lat[nodes] = nodeLat;
                    lon[nodes] = nodeLon;
                    nodes++;
                } else if ("way".equals(type) && highway != null && refCount > 1) {
                    double speed = speed(highway, maxspeed);
                    boolean roundabout = "roundabout".equals(junction) || "circular".equals(junction);
                    boolean forward = true, backward = true;
                    if ("-1".equals(oneway) || "reverse".equals(oneway)) {
                        forward = false;
                    } else if ("yes".equals(oneway) || "true".equals(oneway) || "1".equals(oneway)
                            || (oneway == null && (roundabout || highway.equals("motorway")))) {
                        backward = false;
                    }
                    int previous = ids.get(refs[0]);
                    for (int r = 1; r < refCount; r++) {
                        int current = ids.get(refs[r]);
                        if (previous >= 0 && current >= 0 && previous != current) {
                            float seconds = (float) (distance(lat[previous], lon[previous],
                                    lat[current], lon[current]) / speed);
                            if (edges + 2 > from.length) {
                                from = Arrays.copyOf(from, from.length * 2);
                                to = Arrays.copyOf(to, to.length * 2);
                                cost = Arrays.copyOf(cost, cost.length * 2);
                            }
                            if (forward) {
                                from[edges] = previous;
                                to[edges] = current;
                                cost[edges++] = seconds;
                            }
                            if (backward) {
                                from[edges] = current;
                                to[edges] = previous;
                                cost[edges++] = seconds;
                            }
                        }
                        previous = current;
                    }
                }
            }
            json.endArray();
        }
        json.endObject();
        if (!sawElements) throw new IOException("Respuesta de Overpass incompleta");
        if (edges == 0) throw new IOException("Sin carreteras en la zona descargada");
        return new Graph(nodes, lat, lon, from, to, cost, edges);
    }

    /** Velocidad estimada en m/s según el tipo de vía y el límite señalizado. */
    static double speed(String highway, String maxspeed) {
        double kmh;
        switch (highway) {
            case "motorway": kmh = 120; break;
            case "trunk": kmh = 100; break;
            case "primary": kmh = 80; break;
            case "secondary": kmh = 70; break;
            case "tertiary": kmh = 60; break;
            case "motorway_link": kmh = 60; break;
            case "trunk_link": kmh = 50; break;
            case "primary_link": case "secondary_link": case "tertiary_link": kmh = 40; break;
            case "unclassified": kmh = 45; break;
            case "residential": case "road": kmh = 30; break;
            case "living_street": kmh = 10; break;
            case "service": kmh = 15; break;
            default: kmh = 30;
        }
        double signed = parseMaxspeed(maxspeed);
        // Se conduce algo por debajo del límite: cruces, semáforos y tráfico.
        if (signed > 0) kmh = Math.min(signed * 0.9, Math.max(kmh, signed * 0.75));
        return Math.min(kmh, 130) / 3.6;
    }

    private static double parseMaxspeed(String value) {
        if (value == null) return -1;
        String v = value.trim().toLowerCase(Locale.ROOT);
        switch (v) {
            case "es:urban": case "fr:urban": case "it:urban": case "pt:urban": return 50;
            case "es:rural": return 90;
            case "es:motorway": return 120;
            case "walk": return 6;
            default: break;
        }
        int end = 0;
        while (end < v.length() && Character.isDigit(v.charAt(end))) end++;
        if (end == 0) return -1;
        double number = Double.parseDouble(v.substring(0, end));
        return v.contains("mph") ? number * 1.609 : number;
    }

    // ---------------------------------------------------------------- OSRM

    private Route osrm(double fromLat, double fromLon, double toLat, double toLon)
            throws IOException, NoRouteException {
        String url = OSRM + String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f",
                fromLon, fromLat, toLon, toLat) + "?overview=full&geometries=geojson&steps=false";
        try (InputStream in = request(url, null)) {
            JsonReader json = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String code = null;
            double seconds = -1;
            List<double[]> points = new ArrayList<>();
            json.beginObject();
            while (json.hasNext()) {
                String name = json.nextName();
                if (name.equals("code")) {
                    code = json.nextString();
                } else if (name.equals("routes")) {
                    json.beginArray();
                    if (json.hasNext()) {
                        json.beginObject();
                        while (json.hasNext()) {
                            String field = json.nextName();
                            if (field.equals("duration")) {
                                seconds = json.nextDouble();
                            } else if (field.equals("geometry")) {
                                json.beginObject();
                                while (json.hasNext()) {
                                    if (!json.nextName().equals("coordinates")) {
                                        json.skipValue();
                                        continue;
                                    }
                                    json.beginArray();
                                    while (json.hasNext()) {
                                        json.beginArray();
                                        double lon = json.nextDouble();
                                        double lat = json.nextDouble();
                                        while (json.hasNext()) json.skipValue();
                                        json.endArray();
                                        points.add(new double[]{lat, lon});
                                    }
                                    json.endArray();
                                }
                                json.endObject();
                            } else {
                                json.skipValue();
                            }
                        }
                        json.endObject();
                    }
                    while (json.hasNext()) json.skipValue();
                    json.endArray();
                } else {
                    json.skipValue();
                }
            }
            json.endObject();
            if (!"Ok".equals(code) || points.size() < 2 || seconds < 0) {
                throw new NoRouteException("No se encontró una ruta en coche a ese destino.");
            }
            double[] lat = new double[points.size()];
            double[] lon = new double[points.size()];
            for (int i = 0; i < lat.length; i++) {
                lat[i] = points.get(i)[0];
                lon[i] = points.get(i)[1];
            }
            return new Route(lat, lon, seconds, "OSRM");
        }
    }

    // ---------------------------------------------------------------- Nominatim

    /** Búsqueda de direcciones en OpenStreetMap, priorizando resultados cercanos. */
    List<Place> search(String query, double nearLat, double nearLon) throws IOException {
        String url = NOMINATIM + "?format=jsonv2&limit=5&accept-language=es&q="
                + URLEncoder.encode(query, "UTF-8")
                + String.format(Locale.ROOT, "&viewbox=%.4f,%.4f,%.4f,%.4f",
                nearLon - 1, nearLat + 1, nearLon + 1, nearLat - 1);
        List<Place> places = new ArrayList<>();
        try (InputStream in = request(url, null)) {
            JsonReader json = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            json.beginArray();
            while (json.hasNext()) {
                String label = null;
                double lat = Double.NaN, lon = Double.NaN;
                json.beginObject();
                while (json.hasNext()) {
                    String name = json.nextName();
                    if (json.peek() != JsonToken.STRING) {
                        json.skipValue();
                    } else if (name.equals("display_name")) {
                        label = json.nextString();
                    } else if (name.equals("lat")) {
                        lat = parse(json.nextString());
                    } else if (name.equals("lon")) {
                        lon = parse(json.nextString());
                    } else {
                        json.skipValue();
                    }
                }
                json.endObject();
                if (label != null && !Double.isNaN(lat) && !Double.isNaN(lon)) {
                    places.add(new Place(label, lat, lon));
                }
            }
            json.endArray();
        }
        return places;
    }

    private static double parse(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ---------------------------------------------------------------- Utilidades

    private InputStream request(String url, String form) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(70_000);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Accept", "application/json");
        if (form != null) {
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            byte[] body = form.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
        }
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("HTTP " + status);
        }
        return new BufferedInputStream(connection.getInputStream(), 1 << 16);
    }

    static double distance(double aLat, double aLon, double bLat, double bLon) {
        double dLat = Math.toRadians(bLat - aLat);
        double dLon = Math.toRadians(bLon - aLon);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(aLat))
                * Math.cos(Math.toRadians(bLat)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Montículo binario mínimo (prioridad, nodo) con borrado perezoso. */
    private static final class MinHeap {
        private double[] keys = new double[1024];
        private int[] values = new int[1024];
        private int size;

        boolean isEmpty() {
            return size == 0;
        }

        void push(double key, int value) {
            if (size == keys.length) {
                keys = Arrays.copyOf(keys, size * 2);
                values = Arrays.copyOf(values, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int up = (i - 1) >>> 1;
                if (keys[up] <= key) break;
                keys[i] = keys[up];
                values[i] = values[up];
                i = up;
            }
            keys[i] = key;
            values[i] = value;
        }

        int pop() {
            int top = values[0];
            double key = keys[--size];
            int value = values[size];
            int i = 0;
            while (true) {
                int child = 2 * i + 1;
                if (child >= size) break;
                if (child + 1 < size && keys[child + 1] < keys[child]) child++;
                if (keys[child] >= key) break;
                keys[i] = keys[child];
                values[i] = values[child];
                i = child;
            }
            keys[i] = key;
            values[i] = value;
            return top;
        }
    }

    /** Tabla hash de direccionamiento abierto id OSM → índice, sin objetos por entrada. */
    private static final class LongIntMap {
        private static final long EMPTY = Long.MIN_VALUE;
        private long[] keys = new long[1 << 15];
        private int[] values = new int[1 << 15];
        private int size;

        LongIntMap() {
            Arrays.fill(keys, EMPTY);
        }

        int get(long key) {
            int mask = keys.length - 1;
            for (int i = mix(key) & mask; ; i = (i + 1) & mask) {
                if (keys[i] == key) return values[i];
                if (keys[i] == EMPTY) return -1;
            }
        }

        void put(long key, int value) {
            if (size * 2 >= keys.length) grow();
            int mask = keys.length - 1;
            int i = mix(key) & mask;
            while (keys[i] != EMPTY && keys[i] != key) i = (i + 1) & mask;
            if (keys[i] == EMPTY) size++;
            keys[i] = key;
            values[i] = value;
        }

        private void grow() {
            long[] oldKeys = keys;
            int[] oldValues = values;
            keys = new long[oldKeys.length * 2];
            values = new int[oldKeys.length * 2];
            Arrays.fill(keys, EMPTY);
            size = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldKeys[i] != EMPTY) put(oldKeys[i], oldValues[i]);
            }
        }

        private static int mix(long key) {
            long h = key * 0x9E3779B97F4A7C15L;
            return (int) (h ^ (h >>> 32));
        }
    }
}
