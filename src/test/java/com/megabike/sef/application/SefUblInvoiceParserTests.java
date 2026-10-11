package com.megabike.sef.application;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SefUblInvoiceParserTests {
	@Test
	void preservesCaprioloPieceAndKitQuantitiesFromInvoiceXml() {
		String xml = """
				<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
				 xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
				 xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
				 <cac:InvoiceLine><cbc:ID>1</cbc:ID><cbc:InvoicedQuantity unitCode="H87">5</cbc:InvoicedQuantity></cac:InvoiceLine>
				 <cac:InvoiceLine><cbc:ID>2</cbc:ID><cbc:InvoicedQuantity unitCode="H87">3</cbc:InvoicedQuantity></cac:InvoiceLine>
				 <cac:InvoiceLine><cbc:ID>3</cbc:ID><cbc:InvoicedQuantity unitCode="H87">5</cbc:InvoicedQuantity></cac:InvoiceLine>
				 <cac:InvoiceLine><cbc:ID>4</cbc:ID><cbc:InvoicedQuantity unitCode="H87">10</cbc:InvoicedQuantity></cac:InvoiceLine>
				 <cac:InvoiceLine><cbc:ID>5</cbc:ID><cbc:InvoicedQuantity unitCode="H87">2</cbc:InvoicedQuantity></cac:InvoiceLine>
				 <cac:InvoiceLine><cbc:ID>6</cbc:ID><cbc:InvoicedQuantity unitCode="XKI">1</cbc:InvoicedQuantity></cac:InvoiceLine>
				 <cac:InvoiceLine><cbc:ID>7</cbc:ID><cbc:InvoicedQuantity unitCode="H87">2</cbc:InvoicedQuantity></cac:InvoiceLine>
				 <cac:InvoiceLine><cbc:ID>8</cbc:ID><cbc:InvoicedQuantity unitCode="H87">10</cbc:InvoicedQuantity></cac:InvoiceLine>
				</Invoice>
				""";
		var lines = new SefUblInvoiceParser().parse(xml.getBytes(StandardCharsets.UTF_8)).lines();
		assertThat(lines).extracting(SefUblInvoiceParser.ParsedLine::quantity)
				.containsExactlyElementsOf(List.of(5, 3, 5, 10, 2, 1, 2, 10).stream().map(BigDecimal::valueOf).toList());
		assertThat(lines.get(5).unitCode()).isEqualTo("XKI");
	}
}
