package org.docear.plugin.mcp.webchat;

import java.util.List;
import java.util.Map;

/**
 * Headless checks: public chips never leak server prompts; unknown ids are rejected.
 */
public final class GuestCatalogStandaloneTest {
	private GuestCatalogStandaloneTest() {
	}

	public static void main(final String[] args) {
		assertFind();
		assertPublicListHidesPrompt();
		assertSystemPrompt();
		System.out.println("GuestCatalogStandaloneTest OK");
	}

	private static void assertFind() {
		final GuestCatalog.Preset what = GuestCatalog.find("what");
		if (what == null || what.prompt == null || what.prompt.length() < 10) {
			throw new IllegalStateException("missing what preset");
		}
		if (GuestCatalog.find("privacy") == null || GuestCatalog.find("guest-limit") == null) {
			throw new IllegalStateException("missing privacy/guest-limit");
		}
		if (GuestCatalog.find("nope") != null || GuestCatalog.find("") != null || GuestCatalog.find(null) != null) {
			throw new IllegalStateException("unknown preset must be null");
		}
		if (GuestCatalog.find(" WHAT ") == null) {
			throw new IllegalStateException("trim id");
		}
	}

	private static void assertPublicListHidesPrompt() {
		final List pub = GuestCatalog.listPublic();
		if (pub == null || pub.size() != 6) {
			throw new IllegalStateException("expected 6 public presets, got " + (pub == null ? "null" : Integer.valueOf(pub.size())));
		}
		for (int i = 0; i < pub.size(); i++) {
			final Map row = (Map) pub.get(i);
			if (row.containsKey("prompt") || row.get("id") == null || row.get("title") == null) {
				throw new IllegalStateException("public row leaked prompt or missing fields: " + row);
			}
			if (GuestCatalog.find(String.valueOf(row.get("id"))) == null) {
				throw new IllegalStateException("public id not findable: " + row.get("id"));
			}
		}
	}

	private static void assertSystemPrompt() {
		final String sys = GuestCatalog.systemPrompt();
		if (sys.indexOf("搜集箱") < 0 || sys.indexOf("私人导图") < 0) {
			throw new IllegalStateException("system prompt missing guest constraints");
		}
	}
}
