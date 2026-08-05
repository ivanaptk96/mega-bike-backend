package com.megabike.sef.infrastructure;

import com.megabike.sef.infrastructure.dto.SefPurchaseInvoiceOverviewDto;
import com.megabike.sef.infrastructure.dto.SefPurchaseInvoiceStatusChangeDto;
import com.megabike.sef.infrastructure.dto.SefPurchaseInvoicesDto;
import com.megabike.sef.infrastructure.dto.SefSimplePurchaseInvoiceDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class SefPublicApiClient {

	private static final DateTimeFormatter QUERY_DATE_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final RestClient restClient;

	public SefPublicApiClient(RestClient sefRestClient) {
		this.restClient = sefRestClient;
	}

	public SefPurchaseInvoicesDto getPurchaseInvoiceIds(String apiKey, String status, Instant dateFrom, Instant dateTo) {
		try {
			return restClient.post()
					.uri(uriBuilder -> uriBuilder
							.path("/api/publicApi/purchase-invoice/ids")
							.queryParamIfPresent("status", java.util.Optional.ofNullable(status))
							.queryParamIfPresent("dateFrom", java.util.Optional.ofNullable(format(dateFrom)))
							.queryParamIfPresent("dateTo", java.util.Optional.ofNullable(format(dateTo)))
							.build())
					.header("ApiKey", apiKey)
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(SefPurchaseInvoicesDto.class);
		} catch (RestClientResponseException exception) {
			throw toSefApiException("Failed to get SEF purchase invoice ids.", exception);
		}
	}

	public SefSimplePurchaseInvoiceDto getPurchaseInvoice(String apiKey, long invoiceId) {
		try {
			return restClient.get()
					.uri(uriBuilder -> uriBuilder
							.path("/api/publicApi/purchase-invoice")
							.queryParam("invoiceId", invoiceId)
							.build())
					.header("ApiKey", apiKey)
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(SefSimplePurchaseInvoiceDto.class);
		} catch (RestClientResponseException exception) {
			throw toSefApiException("Failed to get SEF purchase invoice.", exception);
		}
	}

	public List<SefPurchaseInvoiceOverviewDto> getPurchaseInvoiceOverview(String apiKey, String status, Instant dateFrom, Instant dateTo) {
		try {
			return restClient.get()
					.uri(uriBuilder -> uriBuilder
							.path("/api/publicApi/purchase-invoice/overview")
							.queryParamIfPresent("status", java.util.Optional.ofNullable(status))
							.queryParamIfPresent("dateFrom", java.util.Optional.ofNullable(format(dateFrom)))
							.queryParamIfPresent("dateTo", java.util.Optional.ofNullable(format(dateTo)))
							.build())
					.header("ApiKey", apiKey)
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
		} catch (RestClientResponseException exception) {
			throw toSefApiException("Failed to get SEF purchase invoice overview.", exception);
		}
	}

	public List<SefPurchaseInvoiceStatusChangeDto> getPurchaseInvoiceChanges(String apiKey, Instant date) {
		try {
			return restClient.post()
					.uri(uriBuilder -> uriBuilder
							.path("/api/publicApi/purchase-invoice/changes")
							.queryParam("date", format(date))
							.build())
					.header("ApiKey", apiKey)
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
		} catch (RestClientResponseException exception) {
			throw toSefApiException("Failed to get SEF purchase invoice changes.", exception);
		}
	}

	public byte[] downloadPurchaseInvoiceXml(String apiKey, long invoiceId) {
		return download(apiKey, invoiceId, "/api/publicApi/purchase-invoice/xml", "Failed to download SEF purchase invoice XML.");
	}

	public byte[] downloadPurchaseInvoicePdf(String apiKey, long invoiceId) {
		return download(apiKey, invoiceId, "/api/publicApi/purchase-invoice/pdf", "Failed to download SEF purchase invoice PDF.");
	}

	public String subscribeForStatusChangeNotifications(String apiKey) {
		try {
			return restClient.post()
					.uri("/api/publicApi/subscribe")
					.header("ApiKey", apiKey)
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(String.class);
		} catch (RestClientResponseException exception) {
			throw toSefApiException("Failed to subscribe for SEF status change notifications.", exception);
		}
	}

	private byte[] download(String apiKey, long invoiceId, String path, String errorMessage) {
		try {
			return restClient.get()
					.uri(uriBuilder -> uriBuilder.path(path).queryParam("invoiceId", invoiceId).build())
					.header("ApiKey", apiKey)
					.retrieve()
					.body(byte[].class);
		} catch (RestClientResponseException exception) {
			throw toSefApiException(errorMessage, exception);
		}
	}

	private String format(Instant instant) {
		if (instant == null) {
			return null;
		}
		return QUERY_DATE_FORMAT.format(instant.atOffset(ZoneOffset.UTC));
	}

	private SefApiException toSefApiException(String message, RestClientResponseException exception) {
		return new SefApiException(message, exception.getStatusCode(), exception.getResponseBodyAsString());
	}
}
