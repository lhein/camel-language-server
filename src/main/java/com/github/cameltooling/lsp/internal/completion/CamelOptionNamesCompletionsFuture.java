/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cameltooling.lsp.internal.completion;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import com.github.cameltooling.lsp.internal.catalog.model.BaseOptionModel;
import com.github.cameltooling.lsp.internal.catalog.model.ComponentModel;
import com.github.cameltooling.lsp.internal.catalog.model.EndpointOptionModel;
import com.github.cameltooling.lsp.internal.catalog.util.ModelHelper;
import com.github.cameltooling.lsp.internal.instancemodel.CamelUriElementInstance;
import com.github.cameltooling.lsp.internal.instancemodel.OptionParamKeyURIInstance;
import com.github.cameltooling.lsp.internal.instancemodel.OptionParamURIInstance;

public class CamelOptionNamesCompletionsFuture implements Function<CamelCatalog, List<CompletionItem>>  {

	private CamelUriElementInstance uriElement;
	private String camelComponentName;
	private boolean isProducer;
	private String filterString;
	private int positionInCamelURI;
	private Set<OptionParamURIInstance> alreadyDefinedOptions;

	public CamelOptionNamesCompletionsFuture(CamelUriElementInstance uriElement, String camelComponentName, boolean isProducer, String filterText, int positionInCamelURI, Set<OptionParamURIInstance> alreadyDefinedOptions) {
		this.uriElement = uriElement;
		this.camelComponentName = camelComponentName;
		this.isProducer = isProducer;
		this.filterString = filterText;
		this.positionInCamelURI = positionInCamelURI;
		this.alreadyDefinedOptions = alreadyDefinedOptions;
	}

	@Override
	public List<CompletionItem> apply(CamelCatalog catalog) {
		ComponentModel componentModel = ModelHelper.generateComponentModel(catalog.componentJSonSchema(camelComponentName), true);
		List<EndpointOptionModel> endpointOptions = componentModel.getEndpointOptions();
		Stream<CompletionItem> endpointOptionsFiltered = initialFilter(endpointOptions).map(createCompletionItem(CompletionItemKind.Property));
		
		List<EndpointOptionModel> availableApiProperties = uriElement.findAvailableApiProperties(componentModel);
		Stream<CompletionItem> availableApiPropertiesFiltered = initialFilter(availableApiProperties).map(createCompletionItem(CompletionItemKind.Variable));
		
		return Stream.concat(endpointOptionsFiltered, availableApiPropertiesFiltered)
				// filter duplicated uri options
				.filter(FilterPredicateUtils.removeDuplicatedOptions(alreadyDefinedOptions, positionInCamelURI))
				.filter(FilterPredicateUtils.matchesCompletionFilter(filterString))
				.collect(Collectors.toList());
	}

	private Stream<EndpointOptionModel> initialFilter(List<EndpointOptionModel> endpointOptions) {
		return endpointOptions.stream()
				.filter(endpoint -> "parameter".equals(endpoint.getKind()))
				// filter wrong option groups
				.filter(FilterPredicateUtils.matchesProducerConsumerGroups(isProducer));
	}

	private Function<? super BaseOptionModel, ? extends CompletionItem> createCompletionItem(CompletionItemKind kind) {
		return parameter -> {
			CompletionItem completionItem = new CompletionItem(parameter.getName());
			String insertText = parameter.getName();
			
			boolean hasValue = false;
			if (uriElement instanceof OptionParamKeyURIInstance) {
				OptionParamKeyURIInstance param = (OptionParamKeyURIInstance)uriElement;
				hasValue = param.getOptionParamURIInstance().getValue() != null;
			}
			
			if(!hasValue && parameter.getDefaultValue() != null) {
				insertText += String.format("=%s", parameter.getDefaultValue());
			}
			completionItem.setInsertText(insertText);
			completionItem.setDocumentation(parameter.getDescription());
			completionItem.setDetail(parameter.getJavaType());
			completionItem.setKind(kind);
			CompletionResolverUtils.applyDeprecation(completionItem, parameter.isDeprecated());
			CompletionResolverUtils.applyTextEditToCompletionItem(uriElement, completionItem);
			return completionItem;
		};
	}
}
