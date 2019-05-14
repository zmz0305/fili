// Copyright 2016 Yahoo Inc.
// Licensed under the terms of the Apache license. Please see LICENSE.md file distributed with this work for terms.
package com.yahoo.bard.webservice.web.endpoints;

import static com.yahoo.bard.webservice.web.DefaultResponseFormatType.CSV;

import com.yahoo.bard.webservice.application.ObjectMappersSuite;
import com.yahoo.bard.webservice.util.AllPagesPagination;
import com.yahoo.bard.webservice.util.Pagination;
import com.yahoo.bard.webservice.web.CsvResponse;
import com.yahoo.bard.webservice.web.JsonResponse;
import com.yahoo.bard.webservice.web.apirequest.ApiRequest;
import com.yahoo.bard.webservice.web.apirequest.ApiRequestImpl;
import com.yahoo.bard.webservice.web.apirequest.ResponsePaginator;
import com.yahoo.bard.webservice.web.util.ResponseUtils;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

/**
 * Abstract class making available the common code between the servlets that serve the endpoints.
 */
public abstract class EndpointServlet {

    protected final ObjectMappersSuite objectMappers;
    protected final ResponseUtils responseUtils;
    private final Supplier<Response.ResponseBuilder> responseBuilderSupplier;

    /**
     * Constructor.
     *
     * @param objectMappers  Shared JSON tools
     * @param responseBuilderSupplier  This supplies response builders (which are request-stateful)
     */
    public EndpointServlet(
            ObjectMappersSuite objectMappers,
            Supplier<Response.ResponseBuilder> responseBuilderSupplier
    ) {
        this.objectMappers = objectMappers;
        this.responseUtils = new ResponseUtils();
        this.responseBuilderSupplier = responseBuilderSupplier;
    }

    /**
     * Constructor.
     *
     * @param objectMappers  Shared JSON tools
     */
    @Inject
    public EndpointServlet(ObjectMappersSuite objectMappers) {
        this(objectMappers, () -> Response.status(Response.Status.OK));
    }

    /**
     * Format and build the response as JSON or CSV.
     *
     * @param apiRequest  The api request object
     * @param pagination  The object used to build pages of results and links to other pages
     * @param containerRequestContext  The context of the http request
     * @param builder  The builder for the http response
     * @param rows  The stream that describes the data to be formatted
     * @param jsonName  Top-level title for the JSON data
     * @param csvColumnNames  Header for the CSV data
     * @param <T> The type of rows being processed
     *
     * @return The updated response builder with the new link header added
     */
    protected <T> Response formatResponse(
            ApiRequest apiRequest,
            Pagination pagination,
            ContainerRequestContext containerRequestContext,
            Response.ResponseBuilder builder,
            Stream<T> rows,
            String jsonName,
            NameAliasList csvColumnNames
    ) {
        UriInfo uriInfo = containerRequestContext.getUriInfo();
        StreamingOutput output;

        Map<String, String> responseHeaders = responseUtils.buildResponseFormatHeaders(
                containerRequestContext,
                apiRequest.getDownloadFilename().orElse(null),
                apiRequest.getFormat()
        );
        for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
            builder.header(entry.getKey(), entry.getValue());
        }

        if (CSV.accepts(apiRequest.getFormat())) {
            output = new CsvResponse<>(
                    rows,
                    pagination,
                    uriInfo,
                    csvColumnNames,
                    objectMappers
            ).getResponseStream();
        } else {
            output = new JsonResponse<>(
                    rows,
                    pagination,
                    containerRequestContext.getUriInfo(),
                    jsonName,
                    objectMappers
            ).getResponseStream();

        }
        return builder.entity(output).build();
    }

    /**
     * Create an all pages pagination and then format and build the response as JSON or CSV formats.
     *
     *
     * @param apiRequest  The api request object
     * @param containerRequestContext  The context of the http request
     * @param rows  The stream that describes the data to be formatted
     * @param jsonName  Top-level title for the JSON data
     * @param csvColumnNames  Header for the CSV data
     * @param <T> The type of rows being processed
     *
     * @return The updated response builder with the new link header added
     */
    protected <T> Response paginateAndFormatResponse(
            ApiRequest apiRequest,
            ContainerRequestContext containerRequestContext,
            Collection<T> rows,
            String jsonName,
            NameAliasList csvColumnNames
    ) {
        UriInfo uriInfo = containerRequestContext.getUriInfo();

        Pagination<T> pagination = new AllPagesPagination<>(
                rows,
                apiRequest.getPaginationParameters().orElse(ApiRequestImpl.DEFAULT_PAGINATION)
        );

        Response.ResponseBuilder responseBuilder = Response.status(Response.Status.OK);

        Stream<T> stream = ResponsePaginator.paginate(responseBuilder, pagination, uriInfo);

        return formatResponse(
                apiRequest,
                pagination,
                containerRequestContext,
                responseBuilder,
                stream,
                jsonName,
                csvColumnNames
        );
    }

    /**
     * Use a paginator to paginate and then format and build the response as JSON or CSV.
     *
     * @param apiRequest  The api request object
     * @param containerRequestContext  The context of the http request
     * @param pagination  The pagination for this stream of records
     * @param jsonName  Top-level title for the JSON data
     * @param csvColumnNames  Header for the CSV data
     * @param <T> The type of rows being processed
     *
     * @return The updated response builder with the new link header added
     */
    protected <T> Response paginateAndFormatResponse(
            ApiRequest apiRequest,
            ContainerRequestContext containerRequestContext,
            Pagination<T> pagination,
            String jsonName,
            NameAliasList csvColumnNames
    ) {
        UriInfo uriInfo = containerRequestContext.getUriInfo();

        Response.ResponseBuilder builder = responseBuilderSupplier.get();

        Stream<T> stream = ResponsePaginator.paginate(builder, pagination, uriInfo);
        Response.ResponseBuilder responseBuilder = Response.status(Response.Status.OK);

        return formatResponse(
                apiRequest,
                pagination,
                containerRequestContext,
                responseBuilder,
                stream,
                jsonName,
                csvColumnNames
        );
    }
}
