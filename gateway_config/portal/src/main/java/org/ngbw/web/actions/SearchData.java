package org.ngbw.web.actions;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;

import org.apache.struts2.interceptor.validation.SkipValidation;
import org.ngbw.sdk.WorkbenchSession;
import org.ngbw.sdk.api.core.GenericDataRecordCollection;
import org.ngbw.sdk.api.data.MetaQuery;
import org.ngbw.sdk.api.data.SimpleSearchMetaQuery;
import org.ngbw.sdk.core.shared.SourceDocumentType;
import org.ngbw.sdk.core.types.DataType;
import org.ngbw.sdk.core.types.Dataset;
import org.ngbw.sdk.core.types.EntityType;
import org.ngbw.sdk.core.types.RecordFieldType;
import org.ngbw.sdk.core.types.RecordType;
import org.ngbw.sdk.data.SearchHit;
import org.ngbw.sdk.database.DataRecord;
import org.ngbw.sdk.database.RecordField;
import org.ngbw.sdk.database.SourceDocument;
import org.ngbw.sdk.database.UserDataItem;
import org.ngbw.web.controllers.DatasetController;

@SuppressWarnings("serial")
public class SearchData extends DataManager
{
	/*================================================================
	 * Constants
	 *================================================================*/
	private static final Logger logger = Logger.getLogger(SearchData.class.getName());
	// task form tab label constants
	public static final String BASIC_SEARCH = "Basic Search";
	public static final String ADVANCED_SEARCH = "Advanced Search";
	public static final String RESULTS_PAGE = "Results Page";
	public static final String SEARCH_MANAGER = "Search Manager";
	
	// session attribute key constants
	public static final String QUERY = "query";
	public static final String SEARCH_RESULT = "searchResult";
	public static final String LAST_SEARCH_QUERY = "lastSearchQuery";
	public static final String LAST_SEARCH_DATASETS = "lastSearchParameters";

	// result page constants
	public static final String PAGE_SIZE = "pageSize";
	public static final String PAGE_NUMBER = "pageNumber";
	
	/*================================================================
	 * Properties
	 *================================================================*/
	// search form properties
	private String tab;
	
	// data search form bean
	private String queryString;
	private String entityType;
	private String dataType;
	private String[] selectedDatasets;
	private DatasetController controller;
	
	// search results
	private GenericDataRecordCollection<SearchHit> results;
	
	// search result display page properties
	private SearchHit searchResult;
	
	/*================================================================
	 * Action methods
	 *================================================================*/
	@SkipValidation
	public String input() {
		getLastSearch();
		return INPUT;
	}
	
	@SuppressWarnings("unchecked")
	public String execute() throws Exception {
		//TODO: add support for advanced searches
		// submit the search and forward to the results page
		try {
			if (validateSearch()) {
				setLastSearch();
				String[] selectedDatasets = getSelectedDatasets();
				SimpleSearchMetaQuery query = null;
				long start = System.currentTimeMillis();
				if (selectedDatasets.length == 1)
					query = getWorkbench().getSimpleSearchQuery(
						Dataset.valueOf(selectedDatasets[0]));
				else {
					Set<Dataset> datasets = new HashSet<Dataset>(selectedDatasets.length);
					for (int i=0; i<selectedDatasets.length; i++)
						datasets.add(Dataset.valueOf(selectedDatasets[i]));
					query = getWorkbench().getSimpleSearchQuery(datasets);
				}
				int results = selectedDatasets.length;
				String message = "Submitting query \"" + getQueryString() +
					"\" on " + results + " data set";
				if (results != 1) message += "s";
				message += ".";
				reportUserMessage(message);
				query.execute(getQueryString());
				double elapsed = (System.currentTimeMillis() - start) / 1000.0;
				results = query.getResultCount();
				message = "Query returned " + results + " result";
				if (results != 1) message += "s";
				message += " after " + elapsed + " second";
				if (elapsed != 1.0) message += "s";
				message += ".";
				setQuery(query);
				setPageSize(20);
				setPageNumber(0);
				reportUserMessage(message);
				setTab(RESULTS_PAGE);
			}
			return INPUT;
		} catch (Throwable error) {
			reportError(error, "Error processing search query \"" +
				getQueryString() + "\"");
			return ERROR;
		}
	}
	
	@SkipValidation
	public String reload() {
		resolveConcepts();
		return input();
	}
	
	@SkipValidation
	public String list() {
		setTab(RESULTS_PAGE);
		return INPUT;
	}
	
	@SkipValidation
	public String paginate() {
		setPageNumber(0);
		return list();
	}
	
	@SkipValidation
	public String displayResultItem() {
		// get selected result item index from request param, if present
		String[] index = (String[])getParameters().get(ID);
		if (index != null && index.length > 0) {
			setSearchResult(getResultList().get(Integer.parseInt(index[0])));
			return DISPLAY;
		} else if (getSearchResult() != null) {
			return DISPLAY;
		} else {
			addActionError("You must select a search result item to view its details.");
			setTab(RESULTS_PAGE);
			return INPUT;
		}
	}
	
	@SkipValidation
	public String save() {
		try {
			SearchHit searchHit = getSearchResult();
			UserDataItem dataItem = getWorkbenchSession().
				saveUserDataItem(getUserDataItem(searchHit), getCurrentFolder());
			String message = "Data item \"" + truncateText(dataItem.getLabel()) +
				"\" successfully saved.";
			reportUserMessage(message);
			refreshFolderDataTabs();
			setTab(RESULTS_PAGE);
			return INPUT;
		} catch (Throwable error) {
			reportError(error, "Error saving selected search result item");
			return ERROR;
		}
	}
	
	@SkipValidation
	public String saveSelected() {
		try {
			String[] selectedIds = getSelectedIds();
			if (selectedIds == null || selectedIds.length < 1) {
				addActionError("You must select one or more search result items to save them.");
			} else {
				int saved = saveSelectedDataItems();
				String result = saved + " data item";
				if (saved != 1) result += "s were ";
				else result+= " was ";
				result += " successfully saved.";
				reportUserMessage(result);
				refreshFolderDataTabs();
			}
			setTab(RESULTS_PAGE);
			return INPUT;
		} catch (Throwable error) {
			reportError(error, "Error saving selected search result items");
			return ERROR;
		}
	}
	
	@SkipValidation
	public String changeTab() {
		// get selected tab from request param, if present
		String[] tab = (String[])getParameters().get(TAB);
		if (tab != null && tab.length > 0) {
			setTab(tab[0]);
		} else {
			addActionError("You must select a search category to proceed with search management.");
		}
		return input();
	}
	
	@SkipValidation
	public String setPage() {
		// get selected page from request param, if present
		String[] page = (String[])getParameters().get(PAGE);
		if (page != null && page.length > 0) {
			setPageNumber(Integer.parseInt(page[0]));
		} else {
			addActionError("You must select a page number to change pages.");
		}
		setTab(RESULTS_PAGE);
		return INPUT;
	}
	
	/*================================================================
	 * Search form property accessor methods
	 *================================================================*/
	public String getTab() {
		return tab;
	}

	public void setTab(String tab) {
		if (isValidTab(tab))
			this.tab = tab;
		else throw new RuntimeException("SearchData.setTab() was called " +
			"with an invalid argument.");
	}
	
	public String getQueryString() {
		return queryString;
	}

	public void setQueryString(String queryString) {
		this.queryString = queryString;
	}
	
	public DatasetController getController() {
		if (controller ==  null)
			controller = new DatasetController(getWorkbenchSession());
		return controller;
	}
	
	public void getLastSearch() {
		// retrieve last search parameters from session and populate the page with them
		String query = (String)getSession().get(LAST_SEARCH_QUERY);
		if (query != null && query.trim().equals("") == false)
			setQueryString(query);
		DatasetController controller = (DatasetController)getSession().get(LAST_SEARCH_DATASETS);
		if (controller != null && controller.validate()) {
			this.controller = controller;
			setEntityType(controller.getEntityType().toString());
			setDataType(controller.getDataType().toString());
			Dataset[] selectedDatasets = controller.getSelectedDatasets();
			if (selectedDatasets == null || selectedDatasets.length < 1)
				setSelectedDatasets(null);
			else {
				String[] selectedDatasetsString = new String[selectedDatasets.length];
				for (int i=0; i<selectedDatasets.length; i++) {
					selectedDatasetsString[i] = selectedDatasets[i].toString();
				}
				setSelectedDatasets(selectedDatasetsString);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void setLastSearch() {
		// save last search parameters in session
		String query = getQueryString();
		if (query != null && query.trim().equals("") == false)
			getSession().put(LAST_SEARCH_QUERY, query);
		DatasetController controller = getController();
		if (controller != null && controller.validate())
			getSession().put(LAST_SEARCH_DATASETS, controller);
	}
	
	public String getEntityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}
	
	public String getDataType() {
		return dataType;
	}

	public void setDataType(String dataType) {
		this.dataType = dataType;
	}
	
	public String[] getSelectedDatasets() {
		return selectedDatasets;
	}
	
	public void setSelectedDatasets(String[] selectedDatasets) {
		this.selectedDatasets = selectedDatasets;
	}
	
	/*================================================================
	 * Basic search form property accessor methods
	 *================================================================*/
	public Map<String, String> getEntityTypes() {
		try {
			return mapConceptSet(getController().getEntityTypes(), "EntityType");
		} catch (Throwable error) {
			reportError(error, "Error retrieving list of searchable entity types");
			return null;
		}
	}
	
	public Map<String, String> getDataTypes() {
		try {
			resolveConcepts();
			return mapConceptSet(getController().getDataTypes(), "DataType");
		} catch (Throwable error) {
			reportError(error, "Error retrieving list of searchable data types");
			return null;
		}
	}
	
	public boolean hasDataTypes() {
		Map<String, String> dataTypes = getDataTypes();
		return (dataTypes != null && dataTypes.size() > 0);
	}
	
	public Map<String, String> getDatasets() {
		try {
			resolveConcepts();
			return mapConceptSet(getController().getDatasets(), "Dataset");
		} catch (Throwable error) {
			reportError(error, "Error retrieving list of registered datasets for " +
				"specified entity type and data type");
			return null;
		}
	}
	
	public boolean hasDatasets() {
		Map<String, String> datasets = getDatasets();
		return (datasets != null && datasets.size() > 0);
	}
	
	public String getId(DataRecord dataRecord) {
		RecordField id = dataRecord.getField(RecordFieldType.PRIMARY_ID);
		if (id == null)
			return null;
		else return id.getValueAsString();
	}
	
	public String getLabel(DataRecord dataRecord) {
		RecordField label = dataRecord.getField(RecordFieldType.NAME);
		if (label == null)
			return null;
		else return label.getValueAsString();
	}
	
	/*================================================================
	 * Basic search results page property accessor methods
	 *================================================================*/
	public boolean hasQuery() {
		return (getQuery() != null);
	}

	public GenericDataRecordCollection<SearchHit> getResults() {
		// get the search results stored in the action
		if (results != null) {
			return results;
		}
		// if not found, retrieve the proper search results
		else {
			// get user-submitted query
			MetaQuery query = getQuery();
			if (query == null)
				return null;
			else try {
	 			results = query.getResults(getThisPageFirstElementNumber(),
	 				getThisPageLastElementNumber());
				return results;
			} catch (Throwable error) {
				reportError(error, "Error retrieving search results");
				return null;
			}
		}
	}
	
	public int getResultCount() {
		MetaQuery query = getQuery();
		if (query == null)
			return 0;
		else return query.getResultCount();
	}
	
	public List<RecordFieldType> getRecordFields() {
		GenericDataRecordCollection<SearchHit> results = getResults();
		if (results == null)
			return null;
		else if (results.getRecordType().equals(RecordType.COMPOUND_STRUCTURE)) {
			Set<RecordFieldType> fields = new HashSet<RecordFieldType>();
			//TODO: Remove this HACK to accommodate protein structures
			for (RecordFieldType field : results.getFields()) {
				if (field.equals(RecordFieldType.PRIMARY_ID) ||
					field.equals(RecordFieldType.NAME) ||
					field.equals(RecordFieldType.MOLECULE) ||
					field.equals(RecordFieldType.ORGANISM) ||
					field.equals(RecordFieldType.TYPE))
					fields.add(field);
			}
			return sortRecordFields(fields);
		} else return sortRecordFields(results.getFields());
	}
	
	public List<SearchHit> getResultList() {
		//TODO: might need to cache this for performance purposes
		GenericDataRecordCollection<SearchHit> results = getResults();
		if (results == null)
			return null;
		else return results.toList();
	}
	
	public String getSearchRecordField(SearchHit searchHit, RecordFieldType recordField) {
		if (searchHit == null || recordField == null)
			return null;
		else return truncateText(searchHit.getField(recordField).getValueAsString());
	}
	
	public String getDataset(SearchHit searchHit) {
		if (searchHit == null)
			return null;
		else return searchHit.getDataset().toString();
	}
	
	/*================================================================
	 * Search result display page property accessor methods
	 *================================================================*/
	public MetaQuery getQuery() {
		return (MetaQuery)getSession().get(QUERY);
	}
	
	public SimpleSearchMetaQuery getSimpleQuery() {
		try {
			return (SimpleSearchMetaQuery)getSession().get(QUERY);
		} catch (ClassCastException e) {
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void setQuery(MetaQuery query) {
		getSession().put(QUERY, query);
	}
	
	public SearchHit getSearchResult() {
		// first try the search result stored in the action
		if (searchResult != null)
			return searchResult;
		// if not found, retrieve it from the session
		else {
			searchResult = (SearchHit)getSessionAttribute(SEARCH_RESULT);
			return searchResult;
		}
	}
	
	public void setSearchResult(SearchHit searchHit) {
		setSessionAttribute(SEARCH_RESULT, searchHit);
		searchResult = searchHit;
	}
	
	public String getDataset() {
		SearchHit searchResult = getSearchResult();
		if (searchResult == null)
			return null;
		else return getDataset(searchResult);
	}
	
	public String getDatasetText() {
		String dataset = getDataset();
		if (dataset == null)
			return null;
		else return getConceptLabel("Dataset", dataset);
	}
	
	public List<RecordFieldType> getSearchResultFields() {
		SearchHit searchResult = getSearchResult();
		if (searchResult == null)
			return null;
		else {
			List<RecordField> fields = searchResult.getFields();
			Set<RecordFieldType> fieldLabels = new HashSet<RecordFieldType>(fields.size());
			for (RecordField field : fields) {
				fieldLabels.add(field.getFieldType());
			}
			return sortRecordFields(fieldLabels);
		}
	}
	
	public String getFieldValue(String field) {
		SearchHit searchResult = getSearchResult();
		if (searchResult == null)
			return null;
		else try {
			return searchResult.getField(RecordFieldType.valueOf(field)).getValueAsString();
		} catch (Throwable error) {
			reportError(error, "Error retrieving value of field \"" + field +
				"\" of current search result");
			return null;
		}
	}
	
	public String getFieldValue(RecordFieldType field) {
		SearchHit searchResult = getSearchResult();
		if (searchResult == null)
			return null;
		else try {
			return searchResult.getField(field).getValueAsString();
		} catch (Throwable error) {
			reportError(error, "Error retrieving value of field \"" + field +
				"\" of current search result");
			return null;
		}
	}
	
	public SourceDocument getSourceDocument() {
		// first try the source document stored in the action
		if (sourceDocument != null)
			return sourceDocument;
		// if not found, retrieve it from the workbench
		else try {
			MetaQuery query = getQuery();
			SearchHit searchResult = getSearchResult();
			if (query == null)
				throw new NullPointerException("No search query is present.");
			else if (searchResult == null)
				throw new NullPointerException("No search result is currently selected.");
			else {
				sourceDocument = query.getRecordSource(searchResult);
				return sourceDocument;
			}
		} catch (Throwable error) {
			reportError(error, "Error retrieving source document from current search result");
			return null;
		}
	}
	
	public boolean hasSourceDocument() {
		if (sourceDocument != null)
			return true;
		else try {
			return (getSourceDocument() != null);
		} catch (Throwable error) {
			reportError(error, "Error determining whether current search result " +
				"has a source document");
			return false;
		}
	}
	
	/*================================================================
	 * Page methods
	 *================================================================*/
	//TODO: should have a proper page implementation for this
	public Integer getPageSize() {
		return (Integer)getSession().get(PAGE_SIZE);
	}
	
	public String getPageSizeString() {
		return getPageSize().toString();
	}
	
	@SuppressWarnings("unchecked")
	public void setPageSize(Integer pageSize) {
		getSession().put(PAGE_SIZE, pageSize);
	}
	
	public Integer getPageNumber() {
		return (Integer)getSession().get(PAGE_NUMBER);
	}
	
	@SuppressWarnings("unchecked")
	public void setPageNumber(Integer pageNumber) {
		getSession().put(PAGE_NUMBER, pageNumber);
	}
	
	public boolean isFirstPage() {
		return (getPageNumber() == 0);
	}
	
	public boolean isLastPage() {
		return (getPageNumber() == getLastPageNumber());
	}
	
	public boolean hasPreviousPage() {
		return (getPageNumber() > 0);
	}
	
	public int getPreviousPageNumber() {
		if (hasPreviousPage()) return getPageNumber() - 1;
		else return getPageNumber();
	}
	
	public boolean hasNextPage() {
		return (getPageNumber() < getLastPageNumber());
	}
	
	public int getNextPageNumber() {
		if (hasNextPage()) return getPageNumber() + 1;
		else return getPageNumber();
	}
	
	public int getLastPageNumber() {
		int numberOfPages =
			(int)Math.ceil((double)getQuery().getResultCount() / getPageSize());
		if (numberOfPages <= 0) return 0;
		else return (numberOfPages - 1);
	}
	
	public int getThisPageFirstElementNumber() {
		return (getPageNumber() * getPageSize());
	}
	
	public int getThisPageLastElementNumber() {
		int lastElementNumber =
			getThisPageFirstElementNumber() + getThisPageNumberOfElements() - 1;
		if (lastElementNumber <= 0) return 0;
		else return lastElementNumber;
	}

	public int getThisPageNumberOfElements() {
		if (isLastPage() == false) return getPageSize();
		else return (getQuery().getResultCount() - getThisPageFirstElementNumber());
	}

	/*================================================================
	 * Convenience methods
	 *================================================================*/
	protected boolean isValidTab(String tab) {
		if (tab == null) return false;
		else if (tab.equals(BASIC_SEARCH) ||
			tab.equals(ADVANCED_SEARCH) ||
			tab.equals(RESULTS_PAGE) ||
			tab.equals(SEARCH_MANAGER))
			return true;
		else return false;
	}
	
	protected boolean resolveConcepts() {
		String entityTypeString = getEntityType();
		String dataTypeString = getDataType();
		String[] selectedDatasetsString = getSelectedDatasets();
		
		EntityType entityType;
		if (entityTypeString == null)
			entityType = EntityType.UNKNOWN;
		else entityType = EntityType.valueOf(entityTypeString);
		
		DataType dataType;
		if (dataTypeString == null)
			dataType = DataType.UNKNOWN;
		else dataType = DataType.valueOf(dataTypeString);
		
		Dataset[] selectedDatasets;
		if (selectedDatasetsString == null || selectedDatasetsString.length < 1)
			selectedDatasets = null;
		else {
			selectedDatasets = new Dataset[selectedDatasetsString.length];
			for (int i=0; i<selectedDatasets.length; i++)
				selectedDatasets[i] = Dataset.valueOf(selectedDatasetsString[i]);
		}
		
		DatasetController controller = getController();
		controller.resolve(entityType, dataType);
		if (controller.isValid(selectedDatasets)) {
			controller.setSelectedDatasets(selectedDatasets);
			return true;
		} else {
			controller.resolve(entityType, dataType, selectedDatasets);
			return false;
		}
	}
	
	protected boolean validateSearch() {
		String query = getQueryString();
		if (query == null || query.equals("")) {
			addActionError("Query is required.");
			return false;
		}
		String[] selectedDatasets = getSelectedDatasets();
		if (selectedDatasets == null || selectedDatasets.length < 1) {
			addActionError("You must select one or more data sets to perform a data search.");
			return false;
		}
		else return resolveConcepts();
	}
	
	protected int saveSelectedDataItems() {
		int saved = 0;
        Long id;
		String[] selectedIds = getSelectedIds();
		List<SearchHit> results = getResultList();
		if (selectedIds == null || selectedIds.length < 1)
			return saved;
		else for (int i=0; i<selectedIds.length; i++) {
            id = Long.parseLong ( selectedIds[i].split ( "-" )[1].trim() );
			SearchHit searchHit = results.get ( id.intValue() );
			if ( searchHit == null )
            {
				addActionError ( "Error saving result item with index " +
					id + ": item not found in result list." );
            }
			else try {
				UserDataItem dataItem = getUserDataItem(searchHit);
				dataItem = getWorkbenchSession().saveUserDataItem(dataItem, getCurrentFolder());
				saved++;
			} catch (Throwable error) {
				reportUserError(error, "Error saving result item with index " + i);
			}
		}
		refreshFolderDataTabs();
		return saved;
	}
	
	protected UserDataItem getUserDataItem(SearchHit searchHit) {
		WorkbenchSession session = getWorkbenchSession();
		UserDataItem dataItem = session.getUserDataItemInstance(getCurrentFolder());
		String label = getLabel(searchHit);
		if (label == null)
			return null;
		else if (label.length() > 255)
			label = label.substring(0, 255);
		dataItem.setLabel(label);
		SourceDocumentType documentType =
			getWorkbench().getSourceDocumentType(searchHit.getDataset());
		if (documentType == null)
			return null;
		else {
			dataItem.setEntityType(documentType.getEntityType());
			dataItem.setDataType(documentType.getDataType());
			dataItem.setDataFormat(documentType.getDataFormat());
			try {
				dataItem.setData(getQuery().getRecordSource(searchHit).getData());
			} catch (Throwable error) {
				reportUserError(error, "Error retrieving source data for search item");
				return null;
			}
		}
		return dataItem;
	}
}
