// javac RayTracerTexture.java
// java RayTracerTexture

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
        Scene scene = OBJParser.parse("cube.obj");
        Camera camera = new Camera(new Vector3(0, 0, 3), new Vector3(0, 0, 0), 90);

        // Rotate for 30 degrees around the X-axis
        Matrix4x4 rotationMatrix = RotationUtil.glRotate(30, 1, 0, 0);

        // Apply rotation to the object
        RotationUtil.applyRotation(scene, rotationMatrix);

        // Rotate for 30 degrees around the Y-axis
        rotationMatrix = RotationUtil.glRotate(30, 0, 1, 0);

        // Apply rotation to the object
        RotationUtil.applyRotation(scene, rotationMatrix);

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
            ImageIO.write(image, "png", new File("output.png"));
        }

        private static Color sampleTexture(BufferedImage texture, Vector2 uv) {
            int texWidth = texture.getWidth();
            int texHeight = texture.getHeight();

            // Ensure UV coordinates are within the [0, 1] range
            uv.x = Math.ax(0, Math.min(uv.x, 1));
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

            for (Face face : scene.faces) {
                Vector3 v0 = scene.vertices.get(face.vertices[0]);
                Vector3 v1 = scene.vertices.get(face.vertices[1]);
                Vector3 v2 = scene.vertices.get(face.vertices[2]);

                Intersection intersection = RayTracer.intersectRayTriangle(ray, v0, v1, v2);
                if (intersection.hit && (closestIntersection == null || intersection.distance < closestIntersection.distance)) {
                    closestIntersection = intersection;
                    closestFace = face;
                }
            }

            if (closestIntersection != null && closestFace != null) {
                // Retrieve the correct vertices and UVs for interpolation
            }
        }
    }
}
