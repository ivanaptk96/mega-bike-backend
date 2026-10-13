package com.megabike.inventory.application;

import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative name rules: missing or conflicting attributes produce a group, never a guessed leaf. */
@Component
public class ReceiptCategoryRules {
	public record Match(String slug, String reason) {}
	private static final List<String> SIZES = List.of("10", "12", "14", "16", "18", "20", "24", "26", "27.5", "28", "29");

	public Match suggest(String name) {
		String text = normalize(name);
		if (text.isBlank()) return null;
		// Accessories before bicycle keywords (e.g. "pumpa za bicikl", "kaciga za MTB").
		String accessory = accessory(text);
		if (accessory != null) return new Match(accessory, "Prepoznat naziv vrste opreme.");
		if (has(text, "guma", "gume", "unutrasnja", "spoljna", "spoljasnja")) return tyres(text);
		if (has(text, "lula")) return match("biciklizam-delovi-lule-i-adapteri", "Prepoznato iz naziva: lula volana.");
		if (has(text, "lanac", "lanci")) return match("biciklizam-delovi-lanci", "Prepoznato iz naziva: lanac.");
		if (has(text, "pedal")) return match("biciklizam-delovi-pedale", "Prepoznato iz naziva: pedale.");
		if (has(text, "kaset", "racn")) return match("biciklizam-delovi-kasete-i-racne", "Prepoznato iz naziva: račna/kaseta.");
		if (has(text, "lancanik")) return match("biciklizam-delovi-pogoni-i-lancanici", "Prepoznato iz naziva: lančanik.");
		if (has(text, "kurbla")) return match("delovi-pogon-kurbla", "Prepoznato iz naziva: kurbla.");
		if (has(text, "menjac")) {
			boolean front = has(text, "prednj"), rear = has(text, "zadnj");
			if (has(text, "rucic")) return match("biciklizam-delovi-rucice-menjaca", "Prepoznata ručica menjača; izaberite tip.");
			return match(front == rear ? "delovi-menjac" : front ? "delovi-menjac-prednji" : "delovi-menjac-zadnji",
					front == rear ? "Prepoznat menjač; izaberite prednji ili zadnji." : "Prepoznat položaj menjača iz naziva.");
		}
		if (has(text, "tocak", "tockov")) return match("biciklizam-delovi-tockovi", "Prepoznato iz naziva: točak.");
		if (has(text, "felna", "felne")) return match("biciklizam-delovi-felne-zbice-i-niple", "Prepoznato iz naziva: felna.");
		if (has(text, "grip", "ruck")) return match("biciklizam-delovi-gripovi-i-trake-volana", "Prepoznato iz naziva: ručke/gripovi.");
		if (has(text, "volan")) return match("biciklizam-delovi-volani", "Prepoznato iz naziva: volan.");
		if (has(text, "guralica", "balans") || Pattern.compile("\\bgur[ -]+gur\\b").matcher(text).find()) return match("biciklizam-bicikli-balans-bicikli", "Prepoznato iz naziva: balans/guralica.");
		if (has(text, "tricikl")) return match("bicikli-deciji-tricikl", "Prepoznato iz naziva: tricikl.");
		if (has(text, "bmx")) return match("biciklizam-bicikli-bmx", "Prepoznato iz naziva: BMX.");
		if (has(text, "bic", "bicikl", "bike", "mtb", "gravel", "ebike")) return bikes(text);
		return null;
	}

	private Match tyres(String text) {
		boolean inner = has(text, "unutrasnj"), outer = has(text, "spoljn", "spoljasnj");
		if (inner == outer) return match("delovi-gume", "Prepoznata guma; izaberite unutrašnju ili spoljnu i dimenziju.");
		String parent = inner ? "biciklizam-delovi-unutrasnje-gume" : "biciklizam-delovi-spoljasnje-gume";
		var sizes = sizes(text);
		if (sizes.size() != 1) return match(parent, "Prepoznata " + (inner ? "unutrašnja" : "spoljna") + " guma; dimenzija nedostaje ili nije jednoznačna.");
		String size = sizes.getFirst();
		String slug = "delovi-gume-" + (inner ? "unutrasnje-" : "spoljne-") + size.replace('.', '-');
		if (!inner) return match(slug, "Prepoznato iz naziva: spoljna guma, " + size + "″.");
		List<String> valves = new ArrayList<>();
		if (has(text, "av", "schrader", "auto")) valves.add("av");
		if (has(text, "presta", "presto", "fv", "sv")) valves.add("presta");
		if (has(text, "dunlop", "dv", "woods")) valves.add("dunlop");
		if (valves.size() != 1) return match(slug, "Prepoznato iz naziva: unutrašnja guma, " + size + "″; izaberite ventil.");
		return match(slug + "-" + valves.getFirst(), "Prepoznato iz naziva: unutrašnja guma, " + size + "″, " + valves.getFirst().toUpperCase(Locale.ROOT) + ".");
	}

	private Match bikes(String text) {
		if (has(text, "elektricn", "ebike") || text.contains("e-bike") || text.contains("e bike")) return match("biciklizam-bicikli-elektricni", "Prepoznat električni bicikl; izaberite namenu.");
		if (has(text, "decij", "junior", "jr")) {
			var sizes = sizes(text).stream().filter(s -> Set.of("12", "14", "16", "18", "20", "24").contains(s)).toList();
			return match(sizes.size() == 1 ? "bicikli-deciji-" + sizes.getFirst() : "biciklizam-bicikli-deciji",
					sizes.size() == 1 ? "Prepoznato iz naziva: dečiji bicikl, " + sizes.getFirst() + "″." : "Prepoznat dečiji bicikl; izaberite dimenziju/tip.");
		}
		if (has(text, "mtb") && has(text, "full") && has(text, "suspension")) return match("bicikli-mtb-full-suspension", "Prepoznato iz naziva: MTB full suspension.");
		if (has(text, "fat")) return match("bicikli-mtb-fat-bike", "Prepoznat Fat Bike; izaberite namenu i dimenziju.");
		if (has(text, "mtb")) {
			var sizes = sizes(text).stream().filter(s -> Set.of("26", "27.5", "29").contains(s)).toList();
			return match(sizes.size() == 1 ? "bicikli-mtb-" + sizes.getFirst().replace('.', '-') : "biciklizam-bicikli-mtb",
					sizes.size() == 1 ? "Prepoznato iz naziva: MTB, " + sizes.getFirst() + "″." : "Prepoznat MTB; izaberite dimenziju/tip.");
		}
		if (has(text, "gravel")) return match("biciklizam-bicikli-gravel", "Prepoznato iz naziva: gravel bicikl.");
		if (has(text, "drumsk", "road")) return match("biciklizam-bicikli-drumski", "Prepoznato iz naziva: drumski bicikl.");
		if (has(text, "sklopiv", "folding")) return match("biciklizam-bicikli-sklopivi", "Prepoznato iz naziva: sklopivi bicikl.");
		if (has(text, "trekking", "treking")) return match("biciklizam-bicikli-trekking", "Prepoznato iz naziva: trekking bicikl.");
		if (has(text, "gradsk", "city")) return match("biciklizam-bicikli-gradski", "Prepoznat gradski bicikl; izaberite namenu.");
		return match("biciklizam-bicikli", "Prepoznat bicikl; izaberite tip.");
	}

	private String accessory(String text) {
		if (has(text, "kacig", "helmet")) return "biciklizam-oprema-kacige";
		if (has(text, "bidon")) return "biciklizam-oprema-bidoni-i-nosaci-bidona";
		if (has(text, "brzinomer", "ciklokompjuter")) return "biciklizam-oprema-ciklokompjuteri-i-gps";
		if (has(text, "brava", "brave", "katanac")) return "biciklizam-oprema-brave";
		if (has(text, "pump")) return "biciklizam-oprema-pumpe";
		if (has(text, "zvonc", "zvon", "trub")) return "biciklizam-oprema-zvona";
		if (has(text, "rukavic")) return "biciklizam-odeca-i-obuca-rukavice";
		if (has(text, "svetl")) return "biciklizam-oprema-svetla";
		if (has(text, "retrovizor")) return "oprema-retrovizori";
		if (has(text, "naocar")) return "oprema-naocare";
		if (has(text, "korp")) return "biciklizam-oprema-korpe";
		if (has(text, "torbic", "bisag")) return "biciklizam-oprema-torbe-i-bisage";
		if (has(text, "pomocn") && has(text, "tocak", "tockov")) return "oprema-pomocni-tockovi";
		if (has(text, "telefon") && has(text, "drzac", "nosac")) return "biciklizam-oprema-nosaci-telefona";
		return null;
	}

	private List<String> sizes(String text) {
		return SIZES.stream().filter(size -> {
			var matcher = Pattern.compile("(?<![a-z0-9.])" + Pattern.quote(size) + "(?![0-9.]|\\s*[-x×]\\s*\\d{3})").matcher(text);
			while (matcher.find()) {
				// In 700 x 28 or ETRTO 28-622, 28 is tyre width, not a 28-inch wheel.
				if (!Pattern.compile("(?:^|\\D)700\\s*[x×]\\s*$").matcher(text.substring(0, matcher.start())).find()) return true;
			}
			return false;
		}).toList();
	}
	private Match match(String slug, String reason) { return new Match(slug, reason); }
	static String normalize(String value) {
		return Normalizer.normalize(Objects.toString(value, "").toLowerCase(Locale.ROOT).replace("đ", "dj"), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").replace(',', '.').replaceAll("\\s+", " ").trim();
	}
	private boolean has(String text, String... prefixes) {
		for (String prefix : prefixes)
			if (Pattern.compile("(?<![a-z0-9])" + Pattern.quote(prefix) + "[a-z]*(?![a-z0-9])").matcher(text).find()) return true;
		return false;
	}
}
