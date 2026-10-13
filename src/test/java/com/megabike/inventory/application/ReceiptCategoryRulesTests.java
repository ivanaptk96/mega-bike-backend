package com.megabike.inventory.application;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReceiptCategoryRulesTests {
	private final ReceiptCategoryRules rules = new ReceiptCategoryRules();

	@Test
	void recognizesInvoiceAbbreviationsAndSerbianSpelling() {
		assertThat(rules.suggest("GUR-GUR MAGNESIUM").slug()).isEqualTo("biciklizam-bicikli-balans-bicikli");
		assertThat(rules.suggest("GURALICA POLAR FUZZY").slug()).isEqualTo("biciklizam-bicikli-balans-bicikli");
		assertThat(rules.suggest("BIC. POLAR JR 14 Dino green").slug()).isEqualTo("bicikli-deciji-14");
		assertThat(rules.suggest("LULA VOLANA 31.8 MTB").slug()).isEqualTo("biciklizam-delovi-lule-i-adapteri");
		assertThat(rules.suggest("Ručke (gripovi)").slug()).isEqualTo("biciklizam-delovi-gripovi-i-trake-volana");
	}

	@Test
	void recognizesTyreSizeAndValveWithoutConfusingDecimals() {
		assertThat(rules.suggest("Unutrašnja guma 29 AV").slug()).isEqualTo("delovi-gume-unutrasnje-29-av");
		assertThat(rules.suggest("UNUTRASNJA GUMA 27,5x2.1 PRESTA").slug()).isEqualTo("delovi-gume-unutrasnje-27-5-presta");
		assertThat(rules.suggest("Spoljašnja guma 26 x 1.95").slug()).isEqualTo("delovi-gume-spoljne-26");
		assertThat(rules.suggest("Unutrašnja guma 28 Dunlop").slug()).isEqualTo("delovi-gume-unutrasnje-28-dunlop");
	}

	@Test
	void asksForMissingOrConflictingAttributesInsteadOfGuessing() {
		assertThat(rules.suggest("Unutrašnja guma 29").slug()).isEqualTo("delovi-gume-unutrasnje-29");
		assertThat(rules.suggest("Unutrašnja guma 26/27.5 AV").slug()).isEqualTo("biciklizam-delovi-unutrasnje-gume");
		assertThat(rules.suggest("Unutrašnja guma 29 AV/FV").slug()).isEqualTo("delovi-gume-unutrasnje-29");
		assertThat(rules.suggest("Guma 29").slug()).isEqualTo("delovi-gume");
		assertThat(rules.suggest("Unutrašnja guma model 2029 SAVANA").slug()).isEqualTo("biciklizam-delovi-unutrasnje-gume");
		assertThat(rules.suggest("Spoljašnja guma 700 x 28c").slug()).isEqualTo("biciklizam-delovi-spoljasnje-gume");
		assertThat(rules.suggest("Spoljašnja guma 28-622").slug()).isEqualTo("biciklizam-delovi-spoljasnje-gume");
	}

	@Test
	void keepsAccessoriesSeparateFromBicyclesAndElectricBikesSeparateFromMtb() {
		assertThat(rules.suggest("Pumpa za bicikl MTB 29").slug()).isEqualTo("biciklizam-oprema-pumpe");
		assertThat(rules.suggest("Kaciga za dečiji bicikl 14").slug()).isEqualTo("biciklizam-oprema-kacige");
		assertThat(rules.suggest("MTB 27.5").slug()).isEqualTo("bicikli-mtb-27-5");
		assertThat(rules.suggest("E-bike MTB 29").slug()).isEqualTo("biciklizam-bicikli-elektricni");
		assertThat(rules.suggest("Lanac za MTB bicikl").slug()).isEqualTo("biciklizam-delovi-lanci");
		assertThat(rules.suggest("Točak za BMX bicikl").slug()).isEqualTo("biciklizam-delovi-tockovi");
		assertThat(rules.suggest("Nepoznat artikal 123")).isNull();
		assertThat(rules.suggest(null)).isNull();
	}
}
