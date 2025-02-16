import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class RayTracerTexture {
    public static void main(String[] args) throws IOException {

        // Load the UV sphere from the OBJ file
        Scene scene = OBJParser.parse("uvSphere.obj");

        Camera camera = new Camera(new Vector3(0, 0, 3), new Vector3(0, 0, 0), 90);

        int width = 800;
        int height = 600;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage texture = ImageIO.read(new File("texture.png")); // Load texture image

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double ndcX = (x + 0.5) / width;
                double ndcY = (y + 0.5) / height;
                double screenX = 2 * ndcX - 1;
                double screenY = 1 - 2 * ndcY;
                double aspectRatio = (double) width / height;
                screenX *= aspectRatio;

                Vector3 rayDirection = new Vector3(screenX, screenY, -1).normalize();
                Ray ray = new Ray(camera.position, rayDirection);

                Color color = traceRay(ray, scene, texture);
                image.setRGB(x, y, color.getRGB());
            }
        }

        ImageIO.write(image, "png", new File("output.png"));

    }

    private static Color sampleTexture(BufferedImage texture, Vector2 uv) {
        int texWidth = texture.getWidth();
        int texHeight = texture.getHeight();

        // Ensure UV coordinates are within the [0, 1] range
        uv.x = Math.max(0, Math.min(uv.x, 1));
        uv.y = Math.max(0, Math.min(uv.y, 1));

        // Convert UV coordinates to texture pixel coordinates
        int texX = (int) (uv.x * (texWidth - 1));
        int texY = (int) (uv.y * (texHeight - 1));

        // Get the color from the texture
        return new Color(texture.getRGB(texX, texY));
    }

    private static Color traceRay(Ray ray, Scene scene, BufferedImage texture) {
        Intersection closestIntersection = null;
        Face closestFace = null;
        Vector3 v0 = null, v1 = null, v2 = null; // Declare v0, v1, v2 here

        for (Face face : scene.faces) {
            v0 = scene.vertices.get(face.vertices[0]);
            v1 = scene.vertices.get(face.vertices[1]);
            v2 = scene.vertices.get(face.vertices[2]);

            Intersection intersection = RayTracer.intersectRayTriangle(ray, v0, v1, v2);
            if (intersection.hit && (closestIntersection == null || intersection.distance < closestIntersection.distance)) {
                closestIntersection = intersection;
                closestFace = face;
            }
        }

        if (closestIntersection != null && closestFace != null) {
            // Retrieve the UV coordinates for the intersection point
            Vector2 uv0 = scene.uvs.get(closestFace.uvs[0]);
            Vector2 uv1 = scene.uvs.get(closestFace.uvs[1]);
            Vector2 uv2 = scene.uvs.get(closestFace.uvs[2]);

            // Calculate barycentric coordinates for the intersection point
            Vector3 barycentricCoords = calculateBarycentricCoordinates(closestIntersection.point, v0, v1, v2);

            // Interpolate the UV coordinates using the barycentric coordinates
            Vector2 interpolatedUV = interpolateUV(barycentricCoords, uv0, uv1, uv2);

            // Sample the texture using the interpolated UV coordinates
            return sampleTexture(texture, interpolatedUV);
        } else {
            return new Color(0, 0, 0); // background color
        }
    }

    private static Vector3 calculateBarycentricCoordinates(Vector3 p, Vector3 a, Vector3 b, Vector3 c) {
        Vector3 v0 = b.subtract(a);
        Vector3 v1 = c.subtract(a);
        Vector3 v2 = p.subtract(a);

        double d00 = v0.dot(v0);
        double d01 = v0.dot(v1);
        double d11 = v1.dot(v1);
        double d20 = v2.dot(v0);
        double d21 = v2.dot(v1);

        double denom = d00 * d11 - d01 * d01;
        double v = (d11 * d20 - d01 * d21) / denom;
        double w = (d00 * d21 - d01 * d20) / denom;
        double u = 1.0 - v - w;

        return new Vector3(u, v, w);
    }

    private static Vector2 interpolateUV(Vector3 barycentricCoords, Vector2 uv0, Vector2 uv1, Vector2 uv2) {
        double u = barycentricCoords.x * uv0.x + barycentricCoords.y * uv1.x + barycentricCoords.z * uv2.x;
        double v = barycentricCoords.x * uv0.y + barycentricCoords.y * uv1.y + barycentricCoords.z * uv2.y;

        // Some OBJ files flip UV coordinates
        v = 1.0 - v;

        return new Vector2(u, v);
    }

}

class OBJParser {
    public static Scene parse(String filePath) throws IOException {
        Scene scene = new Scene();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(" ");
                switch (tokens[0]) {
                    case "v":
                        // Parse vertex (v)
                        scene.vertices.add(new Vector3(
                                Double.parseDouble(tokens[1]),
                                Double.parseDouble(tokens[2]),
                                Double.parseDouble(tokens[3])
                        ));
                        break;
                    case "vt":
                        // Parse texture coordinate (vt)
                        scene.uvs.add(new Vector2(
                                Double.parseDouble(tokens[1]),
                                Double.parseDouble(tokens[2])
                        ));
                        break;
                    case "vn":
                        // Parse vertex normal (vn)
                        scene.normals.add(new Vector3(
                                Double.parseDouble(tokens[1]),
                                Double.parseDouble(tokens[2]),
                                Double.parseDouble(tokens[3])
                        ));
                        break;
                    case "f":
                        int[] faceVertices = new int[tokens.length - 1];
                        int[] faceUVs = new int[tokens.length - 1];
                        int[] faceNormals = new int[tokens.length - 1];

                        for (int i = 0; i < tokens.length - 1; i++) {
                            String[] faceData = tokens[i + 1].split("/");
                            faceVertices[i] = Integer.parseInt(faceData[0]) - 1; // Vertex index
                            faceUVs[i] = faceData.length > 1 && !faceData[1].isEmpty() ? Integer.parseInt(faceData[1]) - 1 : 0; // Texture index
                            faceNormals[i] = faceData.length > 2 && !faceData[2].isEmpty() ? Integer.parseInt(faceData[2]) - 1 : 0; // Normal index
                        }

                        // If it's a quad (4 vertices), split into two triangles
                        if (faceVertices.length == 4) {
                            scene.faces.add(new Face(new int[]{faceVertices[0], faceVertices[1], faceVertices[2]},
                                    new int[]{faceUVs[0], faceUVs[1], faceUVs[2]},
                                    new int[]{faceNormals[0], faceNormals[1], faceNormals[2]}));

                            scene.faces.add(new Face(new int[]{faceVertices[0], faceVertices[2], faceVertices[3]},
                                    new int[]{faceUVs[0], faceUVs[2], faceUVs[3]},
                                    new int[]{faceNormals[0], faceNormals[2], faceNormals[3]}));
                        } else {
                            // If it's a triangle, add it normally
                            scene.faces.add(new Face(faceVertices, faceUVs, faceNormals));
                        }
                        break;
                }
            }
        }
        return scene;
    }
}

class Face {
    int[] vertices;  // Indices of the vertices
    int[] uvs;       // Indices of the texture coordinates
    int[] normals;   // Indices of the vertex normals

    Face(int[] vertices, int[] uvs, int[] normals) {
        this.vertices = vertices;
        this.uvs = uvs;
        this.normals = normals;
    }
}

class Camera {
    Vector3 position;
    Vector3 lookAt;
    double fieldOfView;

    Camera(Vector3 position, Vector3 lookAt, double fieldOfView) {
        this.position = position;
        this.lookAt = lookAt;
        this.fieldOfView = fieldOfView;
    }
}

class Vector3 {
    double x, y, z;

    Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;

    }
    Vector3 cross(Vector3 v) {
        return new Vector3(
                y * v.z - z * v.y,
                z * v.x - x * v.z,
                x * v.y - y * v.x
        );
    }
    Vector3 add(Vector3 v) {
        return new Vector3(x + v.x, y + v.y, z + v.z);
    }

    Vector3 subtract(Vector3 v) {
        return new Vector3(x - v.x, y - v.y, z - v.z);
    }

    Vector3 multiply(double scalar) {
        return new Vector3(x * scalar, y * scalar, z * scalar);
    }

    double dot(Vector3 v) {
        return x * v.x + y * v.y + z * v.z;
    }

    Vector3 normalize() {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new Vector3(x / length, y / length, z / length);
    }
}

class Scene {
    ArrayList<Vector3> vertices = new ArrayList<>();
    ArrayList<Vector2> uvs = new ArrayList<>();
    ArrayList<Vector3> normals = new ArrayList<>(); // Add normals
    ArrayList<Face> faces = new ArrayList<>();
}

class RayTracer {
    public static Intersection intersectRayTriangle(Ray ray, Vector3 v0, Vector3 v1, Vector3 v2) {
        final double EPSILON = 0.0000001;
        Vector3 edge1 = v1.subtract(v0);
        Vector3 edge2 = v2.subtract(v0);
        Vector3 h = ray.direction.cross(edge2);
        double a = edge1.dot(h);
        if (a > -EPSILON && a < EPSILON) {
            return new Intersection(false, 0, null, null);
        }

        double f = 1.0 / a;
        Vector3 s = ray.origin.subtract(v0);
        double u = f * s.dot(h);
        if (u < 0.0 || u > 1.0) {
            return new Intersection(false, 0, null, null);
        }

        Vector3 q = s.cross(edge1);
        double v = f * ray.direction.dot(q);
        if (v < 0.0 || u + v > 1.0) {
            return new Intersection(false, 0, null, null);
        }

        double t = f * edge2.dot(q);
        if (t > EPSILON) {
            Vector3 intersectionPoint = new Vector3(
                    ray.origin.x + ray.direction.x * t,
                    ray.origin.y + ray.direction.y * t,
                    ray.origin.z + ray.direction.z * t
            );
            Vector3 normal = edge1.cross(edge2).normalize();
            return new Intersection(true, t, intersectionPoint, normal);
        } else {
            return new Intersection(false, 0, null, null);
        }
    }
}

class Ray {
    Vector3 origin;
    Vector3 direction;

    Ray(Vector3 origin, Vector3 direction) {
        this.origin = origin;
        this.direction = direction;
    }
}

class Intersection {
    boolean hit;
    double distance;
    Vector3 point;
    Vector3 normal;

    Intersection(boolean hit, double distance, Vector3 point, Vector3 normal) {
        this.hit = hit;
        this.distance = distance;
        this.point = point;
        this.normal = normal;
    }
}

class Vector2 {
    double x, y;

    Vector2(double x, double y) {
        this.x = x;
        this.y = y;
    }
}