/**
 * Copyright 2016 Yurii Rashkovskii
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 */
package graphql.annotations.processor.retrievers;


import graphql.annotations.annotationTypes.GraphQLRelayMutation;
import graphql.annotations.connection.GraphQLConnection;
import graphql.annotations.directives.DirectiveWirer;
import graphql.annotations.directives.DirectiveWiringMapRetriever;
import graphql.annotations.processor.ProcessingElementsContainer;
import graphql.annotations.processor.exceptions.GraphQLAnnotationsException;
import graphql.annotations.processor.retrievers.fieldBuilders.ArgumentBuilder;
import graphql.annotations.processor.retrievers.fieldBuilders.DeprecateBuilder;
import graphql.annotations.processor.retrievers.fieldBuilders.DescriptionBuilder;
import graphql.annotations.processor.retrievers.fieldBuilders.DirectivesBuilder;
import graphql.annotations.processor.retrievers.fieldBuilders.field.FieldDataFetcherBuilder;
import graphql.annotations.processor.retrievers.fieldBuilders.field.FieldNameBuilder;
import graphql.annotations.processor.retrievers.fieldBuilders.method.MethodDataFetcherBuilder;
import graphql.annotations.processor.retrievers.fieldBuilders.method.MethodNameBuilder;
import graphql.annotations.processor.retrievers.fieldBuilders.method.MethodTypeBuilder;
import graphql.annotations.processor.typeFunctions.TypeFunction;
import graphql.annotations.processor.util.CodeRegistryUtil;
import graphql.annotations.processor.util.ConnectionUtil;
import graphql.annotations.processor.util.DataFetcherConstructor;
import graphql.relay.Relay;
import graphql.schema.*;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.annotations.processor.util.ReflectionKit.newInstance;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;

@Component(service = GraphQLFieldRetriever.class, immediate = true)
public class GraphQLFieldRetriever {

    private DataFetcherConstructor dataFetcherConstructor;

    private boolean alwaysPrettify = false;

    public GraphQLFieldRetriever(DataFetcherConstructor dataFetcherConstructor) {
        this.dataFetcherConstructor = dataFetcherConstructor;
    }

    public GraphQLFieldRetriever() {
        this(new DataFetcherConstructor());
    }

    public GraphQLFieldDefinition getField(String parentName, Method method, ProcessingElementsContainer container) throws GraphQLAnnotationsException {
        GraphQLFieldDefinition.Builder builder = newFieldDefinition();
        TypeFunction typeFunction = getTypeFunction(method, container);
        String fieldName = new MethodNameBuilder(method).alwaysPrettify(alwaysPrettify).build();
        builder.name(fieldName);
        GraphQLOutputType outputType = (GraphQLOutputType) new MethodTypeBuilder(method, typeFunction, container, false).build();

        boolean isConnection = ConnectionUtil.isConnection(method, outputType);
        if (isConnection) {
            outputType = getGraphQLConnection(method, outputType, ConnectionUtil.getRelay(method, container), container.getTypeRegistry());
            builder.arguments(ConnectionUtil.getRelay(method, container).getConnectionFieldArguments());
        }
        builder.type(outputType);
        DirectivesBuilder directivesBuilder = new DirectivesBuilder(method, container);
        builder.withDirectives(directivesBuilder.build());
        List<GraphQLArgument> args = new ArgumentBuilder(method, typeFunction, builder, container, outputType).build();
        GraphQLFieldDefinition relayFieldDefinition = handleRelayArguments(method, container, builder, outputType, args);
        builder.description(new DescriptionBuilder(method).build())
                .deprecate(new DeprecateBuilder(method).build())
                .build();

        DataFetcher dataFetcher = new MethodDataFetcherBuilder(method, outputType, typeFunction, container, relayFieldDefinition, args, dataFetcherConstructor, isConnection).build();
        container.getCodeRegistryBuilder().dataFetcher(coordinates(parentName, fieldName), dataFetcher);

        return (GraphQLFieldDefinition) new DirectiveWirer().wire(builder.build(),
                new DirectiveWiringMapRetriever().getDirectiveWiringMap(method, container),
                container.getCodeRegistryBuilder(), parentName);
    }

    public GraphQLFieldDefinition getField(String parentName, Field field, ProcessingElementsContainer container) throws GraphQLAnnotationsException {
        GraphQLFieldDefinition.Builder builder = newFieldDefinition();
        String fieldName = new FieldNameBuilder(field).alwaysPrettify(alwaysPrettify).build();
        builder.name(fieldName);
        TypeFunction typeFunction = getTypeFunction(field, container);

        GraphQLType outputType = typeFunction.buildType(field.getType(), field.getAnnotatedType(), container);
        boolean isConnection = ConnectionUtil.isConnection(field, outputType);
        if (isConnection) {
            outputType = getGraphQLConnection(field, outputType, ConnectionUtil.getRelay(field, container), container.getTypeRegistry());
            builder.arguments(ConnectionUtil.getRelay(field, container).getConnectionFieldArguments());
        }

        DataFetcher dataFetcher = new FieldDataFetcherBuilder(field, dataFetcherConstructor, outputType, typeFunction, container, isConnection).build();
        builder.type((GraphQLOutputType) outputType).description(new DescriptionBuilder(field).build())
                .deprecate(new DeprecateBuilder(field).build());

        container.getCodeRegistryBuilder().dataFetcher(coordinates(parentName, fieldName), dataFetcher);

        GraphQLDirective[] graphQLDirectives = new DirectivesBuilder(field, container).build();
        builder.withDirectives(graphQLDirectives);

        return (GraphQLFieldDefinition) new DirectiveWirer().wire(builder.build(),
                new DirectiveWiringMapRetriever().getDirectiveWiringMap(field, container),
                container.getCodeRegistryBuilder(), parentName);
    }

    public GraphQLInputObjectField getInputField(Method method, ProcessingElementsContainer container, String parentName) throws GraphQLAnnotationsException {
        GraphQLInputObjectField.Builder builder = newInputObjectField();
        builder.name(new MethodNameBuilder(method).alwaysPrettify(alwaysPrettify).build());
        TypeFunction typeFunction = getTypeFunction(method, container);
        GraphQLInputType inputType = (GraphQLInputType) new MethodTypeBuilder(method, typeFunction, container, true).build();
        builder.withDirectives(new DirectivesBuilder(method, container).build());
        return (GraphQLInputObjectField) new DirectiveWirer().wire(builder.type(inputType)
                        .description(new DescriptionBuilder(method).build()).build(),
                new DirectiveWiringMapRetriever().getDirectiveWiringMap(method, container), container.getCodeRegistryBuilder(), parentName
        );
    }

    public GraphQLInputObjectField getInputField(Field field, ProcessingElementsContainer container, String parentName) throws GraphQLAnnotationsException {
        GraphQLInputObjectField.Builder builder = newInputObjectField();
        builder.name(new FieldNameBuilder(field).alwaysPrettify(alwaysPrettify).build());
        TypeFunction typeFunction = getTypeFunction(field, container);
        GraphQLType graphQLType = typeFunction.buildType(true, field.getType(), field.getAnnotatedType(), container);
        builder.withDirectives(new DirectivesBuilder(field, container).build());
        return (GraphQLInputObjectField) new DirectiveWirer().wire(builder.type((GraphQLInputType) graphQLType)
                        .description(new DescriptionBuilder(field).build()).build(),
                new DirectiveWiringMapRetriever().getDirectiveWiringMap(field, container), container.getCodeRegistryBuilder(), parentName);
    }

    private GraphQLFieldDefinition handleRelayArguments(Method method, ProcessingElementsContainer container, GraphQLFieldDefinition.Builder builder, GraphQLOutputType outputType, List<GraphQLArgument> args) {
        GraphQLFieldDefinition relayFieldDefinition = null;
        if (method.isAnnotationPresent(GraphQLRelayMutation.class)) {
            relayFieldDefinition = buildRelayMutation(method, container, builder, outputType, args);

            // Getting the data fetcher from the old field type and putting it as the new type
            String newParentType = relayFieldDefinition.getType().getName();
            relayFieldDefinition.getType().getChildren().forEach(field -> {
                DataFetcher dataFetcher = CodeRegistryUtil.getDataFetcher(container.getCodeRegistryBuilder(), outputType.getName(), (GraphQLFieldDefinition) field);
                container.getCodeRegistryBuilder().dataFetcher(coordinates(newParentType, field.getName()), dataFetcher);
            });

        } else {
            builder.arguments(args);
        }
        return relayFieldDefinition;
    }

    private TypeFunction getTypeFunction(Method method, ProcessingElementsContainer container) {
        graphql.annotations.annotationTypes.GraphQLType annotation = method.getAnnotation(graphql.annotations.annotationTypes.GraphQLType.class);
        TypeFunction typeFunction = container.getDefaultTypeFunction();

        if (annotation != null) {
            typeFunction = newInstance(annotation.value());
        }
        return typeFunction;
    }

    private GraphQLFieldDefinition buildRelayMutation(Method method, ProcessingElementsContainer container, GraphQLFieldDefinition.Builder builder, GraphQLOutputType outputType, List<GraphQLArgument> args) {
        GraphQLFieldDefinition relayFieldDefinition;
        if (!(outputType instanceof GraphQLObjectType || outputType instanceof GraphQLInterfaceType)) {
            throw new RuntimeException("outputType should be an object or an interface");
        }
        StringBuilder titleBuffer = new StringBuilder(method.getName());
        titleBuffer.setCharAt(0, Character.toUpperCase(titleBuffer.charAt(0)));
        String title = titleBuffer.toString();
        List<GraphQLFieldDefinition> fieldDefinitions = outputType instanceof GraphQLObjectType ?
                ((GraphQLObjectType) outputType).getFieldDefinitions() :
                ((GraphQLInterfaceType) outputType).getFieldDefinitions();
        relayFieldDefinition = container.getRelay().mutationWithClientMutationId(title, method.getName(),
                args.stream().
                        map(t -> newInputObjectField().name(t.getName()).type(t.getType()).description(t.getDescription()).build()).
                        collect(Collectors.toList()), fieldDefinitions, new StaticDataFetcher(null));
        builder.arguments(relayFieldDefinition.getArguments()).type(relayFieldDefinition.getType());
        return relayFieldDefinition;
    }


    private TypeFunction getTypeFunction(Field field, ProcessingElementsContainer container) {
        graphql.annotations.annotationTypes.GraphQLType annotation = field.getAnnotation(graphql.annotations.annotationTypes.GraphQLType.class);

        TypeFunction typeFunction = container.getDefaultTypeFunction();

        if (annotation != null) {
            typeFunction = newInstance(annotation.value());
        }
        return typeFunction;
    }

    private GraphQLOutputType getGraphQLConnection(AccessibleObject field, graphql.schema.GraphQLType type, Relay relay, Map<String, graphql.schema.GraphQLType> typeRegistry) {
        if (type instanceof GraphQLNonNull) {
            GraphQLList listType = (GraphQLList) ((GraphQLNonNull) type).getWrappedType();
            return new GraphQLNonNull(internalGetGraphQLConnection(field, listType, relay, typeRegistry));
        } else {
            return internalGetGraphQLConnection(field, (GraphQLList) type, relay, typeRegistry);
        }
    }

    private GraphQLOutputType internalGetGraphQLConnection(AccessibleObject field, GraphQLList listType, Relay relay, Map<String, graphql.schema.GraphQLType> typeRegistry) {
        GraphQLOutputType wrappedType = (GraphQLOutputType) listType.getWrappedType();
        String connectionName = field.getAnnotation(GraphQLConnection.class).name();
        connectionName = connectionName.isEmpty() ? wrappedType.getName() : connectionName;
        GraphQLObjectType edgeType = getActualType(relay.edgeType(connectionName, wrappedType, null, Collections.emptyList()), typeRegistry);
        return getActualType(relay.connectionType(connectionName, edgeType, Collections.emptyList()), typeRegistry);
    }

    private GraphQLObjectType getActualType(GraphQLObjectType type, Map<String, graphql.schema.GraphQLType> typeRegistry) {
        if (typeRegistry.containsKey(type.getName())) {
            type = (GraphQLObjectType) typeRegistry.get(type.getName());
        } else {
            typeRegistry.put(type.getName(), type);
        }
        return type;
    }

    public void setAlwaysPrettify(boolean alwaysPrettify) {
        this.alwaysPrettify = alwaysPrettify;
    }

    @Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    public void setDataFetcherConstructor(DataFetcherConstructor dataFetcherConstructor) {
        this.dataFetcherConstructor = dataFetcherConstructor;
    }

    public void unsetDataFetcherConstructor(DataFetcherConstructor dataFetcherConstructor) {
        this.dataFetcherConstructor = new DataFetcherConstructor();
    }
}
