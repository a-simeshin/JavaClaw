package ai.javaclaw.api.chat;

/** @deprecated Scheduled for removal — will be replaced when migrating from Pebble to REST/SPA. */
@Deprecated(forRemoval = true)
public class Htmx {

    private Htmx() {}

    public static String oobAppend(String id, String content) {
        return "<div id=\"" + id + "\" hx-swap-oob=\"beforeend\">" + content + "</div>";
    }

    public static String oobReplace(String id, String content) {
        return "<div id=\"" + id + "\" hx-swap-oob=\"true\">" + content + "</div>";
    }

    public static String oobInnerHtml(String id, String content) {
        return "<div id=\"" + id + "\" hx-swap-oob=\"innerHTML\">" + content + "</div>";
    }
}
