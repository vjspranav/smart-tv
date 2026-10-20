import java.awt.*; import java.awt.image.BufferedImage; import java.io.File; import javax.imageio.ImageIO;
public class Banner { public static void main(String[] a) throws Exception {
  BufferedImage img = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
  Graphics2D g = img.createGraphics();
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
  g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  g.setColor(Color.BLACK); g.fillRect(0, 0, 320, 180);
  g.setColor(new Color(255, 255, 255, 23));
  for (int x = 6; x < 320; x += 12) for (int y = 6; y < 180; y += 12) g.fillOval(x - 1, y - 1, 2, 2);
  Font f = Font.createFont(Font.TRUETYPE_FONT, new File(a[0])).deriveFont(Font.BOLD, 46f);
  g.setFont(f); g.setColor(Color.WHITE);
  FontMetrics m = g.getFontMetrics(); String t = "FLAMINGO";
  g.drawString(t, (320 - m.stringWidth(t)) / 2, 104);
  g.setColor(new Color(0xff2a1a)); g.fillOval(150, 126, 12, 12);
  ImageIO.write(img, "png", new File(a[1]));
}}
