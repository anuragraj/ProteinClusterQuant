package edu.scripps.yates.pcq.xgmml.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import edu.scripps.yates.annotations.uniprot.xml.Entry;
import edu.scripps.yates.utilities.fasta.FastaParser;
import edu.scripps.yates.utilities.jaxb.xpathquery.JAXBXPathQuery;

public class UniprotAnnotationColumn {
	private final static Logger log = Logger.getLogger(UniprotAnnotationColumn.class);
	private final String columnName;
	private final String xpath;
	private final String subXpath;

	/**
	 * parse a triplet as [xpath, subpath, columnName]
	 * 
	 * @param triplet
	 */
	public UniprotAnnotationColumn(String triplet) {
		if (triplet.contains(",")) {
			final String[] split = triplet.split(",");
			if (split.length == 3) {
				xpath = split[0].trim();
				subXpath = split[1].trim();
				columnName = split[2].trim();
				checkValues();
				return;
			}
		}
		throw new IllegalArgumentException("Invalid value in property 'uniprot_xpath': " + triplet
				+ ". It must contain 3 elements separated by commas.");
	}

	private void checkValues() {
		if ("".equals(xpath)) {
			throw new IllegalArgumentException("First element in the triplet cannot be empty.");
		}
		if ("".equals(columnName)) {
			throw new IllegalArgumentException("Third element in the triplet (column name) cannot be empty.");
		}
	}

	public UniprotAnnotationColumn(String columnName, String xpath, String subXpath) {
		super();
		this.columnName = columnName.trim();
		this.xpath = xpath.trim();
		this.subXpath = subXpath.trim();
		checkValues();
	}

	public String getColumnName() {
		return columnName;
	}

	public List<String> getValuesForProtein(String acc, Map<String, Entry> annotatedProteins) {
		final String uniProtACC = FastaParser.getUniProtACC(acc);
		if (uniProtACC != null && annotatedProteins.containsKey(uniProtACC)) {
			final Entry entry = annotatedProteins.get(uniProtACC);

			final List<String> results = JAXBXPathQuery.query(entry, xpath, subXpath);
			return results;

		}
		return Collections.emptyList();
	}
}
