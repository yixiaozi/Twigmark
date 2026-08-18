package org.docear.plugin.mcp.webchat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public FAQ chips for unauthenticated web chat. Prompts are server-side only;
 * clients send {@code presetId}, never free text.
 */
public final class GuestCatalog {

	private static final List PRESETS = buildPresets();

	private GuestCatalog() {
	}

	public static List listPublic() {
		final List out = new ArrayList();
		for (int i = 0; i < PRESETS.size(); i++) {
			final Preset p = (Preset) PRESETS.get(i);
			final Map row = new LinkedHashMap();
			row.put("id", p.id);
			row.put("title", p.title);
			out.add(row);
		}
		return out;
	}

	public static Preset find(final String id) {
		if (id == null) {
			return null;
		}
		final String key = id.trim();
		if (key.length() == 0) {
			return null;
		}
		for (int i = 0; i < PRESETS.size(); i++) {
			final Preset p = (Preset) PRESETS.get(i);
			if (p.id.equalsIgnoreCase(key)) {
				return p;
			}
		}
		return null;
	}

	public static String systemPrompt() {
		return "你是 Twigmark 的公开介绍助手。当前访客没有登录，看不到任何人的私人导图、待办或财务。"
				+ "禁止声称已经检索到某位用户的笔记；禁止编造导图内容。"
				+ "只用中文、简洁回答（默认 120～280 字）。"
				+ "可以介绍：思维导图库、待办/提醒、标签、MCP（Cursor 等客户端用 API Key 连接）、网页登录后可对导图提问。"
				+ "网页游客只能点预设问题，不能自由输入，以免误查私人数据；想体验完整能力请登录。"
				+ "若问题超出产品介绍，请引导对方使用页面上的「功能搜集箱」提交想法。";
	}

	private static List buildPresets() {
		final List list = new ArrayList();
		list.add(new Preset("what", "Twigmark 是什么？",
				"请用通俗中文介绍 Twigmark：它是个人思维导图知识库，把笔记、待办、标签、提醒放在同一套导图里，"
						+ "并可用 MCP 让 Cursor 等 AI 助手读写。不要编造具体用户的导图。"));
		list.add(new Preset("web-desktop", "网页版和桌面版有什么不同？",
				"比较 Twigmark 网页版与桌面版：桌面端是完整 Freeplane/Docear，可编辑导图；"
						+ "网页可浏览导图库、登录后用大模型提问（通常只读）；游客未登录只能点预设问题。"
						+ "MCP 服务给 Cursor 用，不是给匿名网页游客扫私人库。"));
		list.add(new Preset("mcp", "怎样用 Cursor 连接 MCP？",
				"说明如何把 Cursor 连到 Twigmark MCP：本机或 HTTPS 反代的 /mcp，请求头 Authorization: Bearer <API Key>。"
						+ "Key 有 read/write/owner 角色。不要把 7720 端口直接暴露到公网。不要输出任何真实密钥。"));
		list.add(new Preset("workflow", "导图、待办、标签怎么一起用？",
				"介绍典型用法：用导图记结构，节点可加待办/提醒/优先级，侧栏标签与收藏做检索，"
						+ "AI 用 search_nodes、list_todos 等工具取证。游客看不到真实库，只讲方法。"));
		list.add(new Preset("guest-limit", "为什么游客不能自己打字？",
				"解释限制原因：未登录若允许任意提问，模型可能去搜主人的私人导图。"
						+ "所以游客只能点预设产品问题；登录后才能对导图自由提问。"
						+ "新想法请用「功能搜集箱」。"));
		list.add(new Preset("privacy", "游客能看到我的导图吗？",
				"明确回答：不能。未登录对话没有 MCP 工具，也不会列出导图文件。"
						+ "导图库、待办、对话历史都需要登录。公开问答库是主人主动公开的条目，与导图库不是一回事。"));
		return Collections.unmodifiableList(list);
	}

	public static final class Preset {
		public final String id;
		public final String title;
		public final String prompt;

		Preset(final String id, final String title, final String prompt) {
			this.id = id;
			this.title = title;
			this.prompt = prompt;
		}
	}
}
