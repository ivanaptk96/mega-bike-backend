package com.megabike.sef.application;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class SefUblInvoiceParser {

	private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
	private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

	public ParsedInvoice parse(byte[] xml) {
		Document document = parseDocument(xml);
		Element root = document.getDocumentElement();
		return new ParsedInvoice(
				parseLines(root),
				parseVatSummaries(root),
				parsePayments(root)
		);
	}

	private List<ParsedLine> parseLines(Element root) {
		List<ParsedLine> lines = new ArrayList<>();
		NodeList invoiceLines = root.getElementsByTagNameNS(CAC, "InvoiceLine");
		for (int index = 0; index < invoiceLines.getLength(); index++) {
			Element line = (Element) invoiceLines.item(index);
			Element item = firstChild(line, CAC, "Item");
			Element price = firstChild(line, CAC, "Price");
			Element taxCategory = firstDescendant(item, CAC, "ClassifiedTaxCategory");
			Element quantity = firstChild(line, CBC, "InvoicedQuantity");

			lines.add(new ParsedLine(
					index + 1,
					text(firstChild(line, CBC, "ID")),
					text(firstChild(item, CBC, "Name")),
					text(firstChild(item, CBC, "Description")),
					text(firstDescendant(item, CAC, "SellersItemIdentification"), CBC, "ID"),
					text(firstDescendant(item, CAC, "BuyersItemIdentification"), CBC, "ID"),
					quantity == null ? null : quantity.getAttribute("unitCode"),
					decimal(text(quantity)),
					decimal(text(firstChild(price, CBC, "PriceAmount"))),
					lineDiscount(line),
					decimal(text(firstChild(line, CBC, "LineExtensionAmount"))),
					text(firstChild(taxCategory, CBC, "ID")),
					decimal(text(firstChild(taxCategory, CBC, "Percent"))),
					decimal(text(firstDescendant(line, CBC, "TaxAmount")))
			));
		}
		return lines;
	}

	private List<ParsedVatSummary> parseVatSummaries(Element root) {
		List<ParsedVatSummary> vatSummaries = new ArrayList<>();
		NodeList taxSubtotals = root.getElementsByTagNameNS(CAC, "TaxSubtotal");
		for (int index = 0; index < taxSubtotals.getLength(); index++) {
			Element subtotal = (Element) taxSubtotals.item(index);
			Element taxCategory = firstChild(subtotal, CAC, "TaxCategory");
			String vatCategory = text(firstChild(taxCategory, CBC, "ID"));
			if (vatCategory == null) {
				vatCategory = "UNKNOWN";
			}

			vatSummaries.add(new ParsedVatSummary(
					vatCategory,
					decimal(text(firstChild(taxCategory, CBC, "Percent"))),
					decimal(text(firstChild(subtotal, CBC, "TaxableAmount"))),
					decimal(text(firstChild(subtotal, CBC, "TaxAmount"))),
					text(firstChild(taxCategory, CBC, "TaxExemptionReasonCode")),
					text(firstChild(taxCategory, CBC, "TaxExemptionReason"))
			));
		}
		return vatSummaries;
	}

	private List<ParsedPayment> parsePayments(Element root) {
		List<ParsedPayment> payments = new ArrayList<>();
		NodeList paymentMeans = root.getElementsByTagNameNS(CAC, "PaymentMeans");
		for (int index = 0; index < paymentMeans.getLength(); index++) {
			Element payment = (Element) paymentMeans.item(index);
			Element account = firstDescendant(payment, CAC, "PayeeFinancialAccount");
			String paymentId = text(firstChild(payment, CBC, "PaymentID"));
			String modelNumber = null;
			String referenceNumber = paymentId;
			if (paymentId != null && paymentId.contains(" ")) {
				String[] parts = paymentId.split("\\s+", 2);
				modelNumber = parts[0];
				referenceNumber = parts[1];
			}

			payments.add(new ParsedPayment(
					text(firstChild(payment, CBC, "PaymentMeansCode")),
					paymentId,
					text(firstChild(account, CBC, "ID")),
					text(firstChild(account, CBC, "Name")),
					modelNumber,
					referenceNumber
			));
		}
		return payments;
	}

	private BigDecimal lineDiscount(Element line) {
		NodeList allowanceCharges = line.getElementsByTagNameNS(CAC, "AllowanceCharge");
		BigDecimal total = BigDecimal.ZERO;
		for (int index = 0; index < allowanceCharges.getLength(); index++) {
			Element allowanceCharge = (Element) allowanceCharges.item(index);
			if ("false".equalsIgnoreCase(text(firstChild(allowanceCharge, CBC, "ChargeIndicator")))) {
				BigDecimal amount = decimal(text(firstChild(allowanceCharge, CBC, "Amount")));
				if (amount != null) {
					total = total.add(amount);
				}
			}
		}
		return total.compareTo(BigDecimal.ZERO) == 0 ? null : total;
	}

	private Document parseDocument(byte[] xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			return factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(xml)));
		} catch (Exception exception) {
			String prefix = new String(xml, 0, Math.min(xml.length, 120), StandardCharsets.UTF_8);
			throw new IllegalArgumentException("Failed to parse SEF UBL invoice XML. Prefix: " + prefix, exception);
		}
	}

	private Element firstDescendant(Element parent, String namespace, String localName) {
		if (parent == null) {
			return null;
		}
		NodeList nodes = parent.getElementsByTagNameNS(namespace, localName);
		if (nodes.getLength() == 0) {
			return null;
		}
		return (Element) nodes.item(0);
	}

	private Element firstChild(Element parent, String namespace, String localName) {
		if (parent == null) {
			return null;
		}
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element
					&& namespace.equals(element.getNamespaceURI())
					&& localName.equals(element.getLocalName())) {
				return element;
			}
		}
		return null;
	}

	private String text(Element parent, String namespace, String localName) {
		return text(firstChild(parent, namespace, localName));
	}

	private String text(Element element) {
		if (element == null) {
			return null;
		}
		String value = element.getTextContent();
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private BigDecimal decimal(String value) {
		if (value == null) {
			return null;
		}
		return new BigDecimal(value);
	}

	public record ParsedInvoice(List<ParsedLine> lines, List<ParsedVatSummary> vatSummaries, List<ParsedPayment> payments) {
	}

	public record ParsedLine(
			int lineNumber,
			String externalLineId,
			String itemName,
			String itemDescription,
			String sellerItemId,
			String buyerItemId,
			String unitCode,
			BigDecimal quantity,
			BigDecimal unitPrice,
			BigDecimal discountAmount,
			BigDecimal lineAmountWithoutVat,
			String vatCategory,
			BigDecimal vatRate,
			BigDecimal vatAmount
	) {
	}

	public record ParsedVatSummary(
			String vatCategory,
			BigDecimal vatRate,
			BigDecimal taxableAmount,
			BigDecimal vatAmount,
			String exemptionReasonCode,
			String exemptionReason
	) {
	}

	public record ParsedPayment(
			String paymentMeansCode,
			String paymentId,
			String accountNumber,
			String accountName,
			String modelNumber,
			String referenceNumber
	) {
	}
}
