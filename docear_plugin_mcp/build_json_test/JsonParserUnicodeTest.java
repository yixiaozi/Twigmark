import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
public class JsonParserUnicodeTest {
  public static void main(String[] args) {
    JsonValue v = JsonParser.parse("{\"q\":\"\\u7528\\u6237\\u60f3\\u6d4b\\u8bd5\"}");
    String q = v.asMap().get("q").asString();
    System.out.println("parsed=" + q);
    System.out.println("ok=" + "\u7528\u6237\u60f3\u6d4b\u8bd5".equals(q));
  }
}