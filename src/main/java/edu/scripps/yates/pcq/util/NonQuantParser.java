package edu.scripps.yates.pcq.util;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;

import edu.scripps.yates.census.analysis.util.KeyUtils;
import edu.scripps.yates.census.read.AbstractQuantParser;
import edu.scripps.yates.census.read.model.QuantifiedPeptide;
import edu.scripps.yates.census.read.model.QuantifiedProteinFromDBIndexEntry;
import edu.scripps.yates.census.read.model.StaticQuantMaps;
import edu.scripps.yates.census.read.model.interfaces.QuantifiedPSMInterface;
import edu.scripps.yates.census.read.model.interfaces.QuantifiedPeptideInterface;
import edu.scripps.yates.census.read.model.interfaces.QuantifiedProteinInterface;
import edu.scripps.yates.dbindex.DBIndexStoreException;
import edu.scripps.yates.dbindex.IndexedProtein;
import edu.scripps.yates.dbindex.util.PeptideNotFoundInDBIndexException;
import edu.scripps.yates.dtaselectparser.DTASelectParser;
import edu.scripps.yates.dtaselectparser.util.DTASelectPSM;
import edu.scripps.yates.dtaselectparser.util.DTASelectProtein;
import edu.scripps.yates.pcq.model.NonQuantifiedPSM;
import edu.scripps.yates.pcq.model.NonQuantifiedProtein;
import edu.scripps.yates.utilities.progresscounter.ProgressCounter;
import edu.scripps.yates.utilities.progresscounter.ProgressPrintingType;

public class NonQuantParser extends AbstractQuantParser {
	private final static Logger log = Logger.getLogger(NonQuantParser.class);
	private final DTASelectParser dtaSelectParser;

	public NonQuantParser(DTASelectParser dtaSelectParser) throws PatternSyntaxException, IOException {
		this.dtaSelectParser = dtaSelectParser;
		enableProteinMergingBySecondaryAccessions(dtaSelectParser.getUniprotProteinLocalRetriever(),
				dtaSelectParser.getUniprotVersion());
		setDbIndex(dtaSelectParser.getDBIndex());
		setDecoyPattern(dtaSelectParser.getDecoyPattern());
		setIgnoreNotFoundPeptidesInDB(dtaSelectParser.isIgnoreNotFoundPeptidesInDB());
		setIgnoreTaxonomies(dtaSelectParser.isIgnoreTaxonomies());
		setRetrieveFastaIsoforms(dtaSelectParser.isRetrieveFastaIsoforms());
		setIgnoreACCFormat(dtaSelectParser.isIgnoreACCFormat());
		// do not clear static maps, in order to get the same objects than a
		// previous quantparser
		super.clearStaticMapsBeforeReading = false;

	}

	@Override
	protected void process() {
		try {
			processed = false;
			final Map<String, DTASelectPSM> psms = dtaSelectParser.getDTASelectPSMsByPSMID();
			// wrapping dtaselect psms to PCQ
			log.info("Wrapping " + psms.size() + " PSMs from DTASelect parser into PCQ");
			final ProgressCounter counter = new ProgressCounter(psms.size(), ProgressPrintingType.PERCENTAGE_STEPS, 0);
			for (final DTASelectPSM psm : psms.values()) {
				counter.increment();
				final String printIfNecessary = counter.printIfNecessary();
				if (!"".equals(printIfNecessary)) {
					log.info("Wrapping PSMs to PCQ... " + printIfNecessary);
				}
				processPSM(psm);

			}
			processed = true;
		} catch (final IOException e) {
			e.printStackTrace();
		} catch (final DBIndexStoreException e) {
			e.printStackTrace();
		}

	}

	private void processPSM(DTASelectPSM psm) throws IOException, DBIndexStoreException {

		final String experimentKey = psm.getRawFileName();

		QuantifiedPSMInterface quantifiedPSM = new NonQuantifiedPSM(psm);
		for (final String inputFileName : dtaSelectParser.getInputFilePathes()) {
			quantifiedPSM.getFileNames().add(inputFileName);
		}
		final String psmKey = KeyUtils.getSpectrumKey(quantifiedPSM, true);
		// in case of TMT, the psm may have been created before
		if (StaticQuantMaps.psmMap.containsKey(psmKey)) {
			quantifiedPSM = StaticQuantMaps.psmMap.getItem(psmKey);
		}
		StaticQuantMaps.psmMap.addItem(quantifiedPSM);

		// psms.add(quantifiedPSM);
		// add to map
		if (!localPsmMap.containsKey(quantifiedPSM.getKey())) {
			localPsmMap.put(quantifiedPSM.getKey(), quantifiedPSM);
		}

		// create the peptide
		QuantifiedPeptideInterface quantifiedPeptide = null;
		final String peptideKey = KeyUtils.getSequenceKey(quantifiedPSM, true);
		if (StaticQuantMaps.peptideMap.containsKey(peptideKey)) {
			quantifiedPeptide = StaticQuantMaps.peptideMap.getItem(peptideKey);
		} else {
			quantifiedPeptide = new QuantifiedPeptide(quantifiedPSM, isIgnoreTaxonomies());
		}
		StaticQuantMaps.peptideMap.addItem(quantifiedPeptide);

		quantifiedPSM.setQuantifiedPeptide(quantifiedPeptide, true);
		// add peptide to map
		if (!localPeptideMap.containsKey(peptideKey)) {
			localPeptideMap.put(peptideKey, quantifiedPeptide);
		}

		if (dbIndex != null) {
			final String cleanSeq = quantifiedPSM.getSequence();
			final Set<IndexedProtein> indexedProteins = dbIndex.getProteins(cleanSeq);
			if (indexedProteins.isEmpty()) {
				if (!super.ignoreNotFoundPeptidesInDB) {
					throw new PeptideNotFoundInDBIndexException("The peptide " + cleanSeq
							+ " is not found in Fasta DB.\nReview the default indexing parameters such as the number of allowed misscleavages.");
				}
				// log.warn("The peptide " + cleanSeq +
				// " is not found in Fasta DB.");
				// continue;
			}
			// create a new Quantified Protein for each
			// indexedProtein
			for (final IndexedProtein indexedProtein : indexedProteins) {
				final String proteinKey = KeyUtils.getProteinKey(indexedProtein, isIgnoreACCFormat());
				QuantifiedProteinInterface quantifiedProtein = null;
				if (StaticQuantMaps.proteinMap.containsKey(proteinKey)) {
					quantifiedProtein = StaticQuantMaps.proteinMap.getItem(proteinKey);
				} else {
					quantifiedProtein = new QuantifiedProteinFromDBIndexEntry(indexedProtein, isIgnoreTaxonomies(),
							isIgnoreACCFormat());
				}
				StaticQuantMaps.proteinMap.addItem(quantifiedProtein);
				// add psm to the proteins
				quantifiedProtein.addPSM(quantifiedPSM, true);
				// add protein to the psm
				quantifiedPSM.addQuantifiedProtein(quantifiedProtein, true);
				// add peptide to the protein
				quantifiedProtein.addPeptide(quantifiedPeptide, true);
				// add to the map (if it was already there
				// is not a problem, it will be only once)
				addToMap(proteinKey, proteinToPeptidesMap, KeyUtils.getSequenceKey(quantifiedPSM, true));
				// add protein to protein map
				localProteinMap.put(proteinKey, quantifiedProtein);
				// add to protein-experiment map
				addToMap(experimentKey, experimentToProteinsMap, proteinKey);

			}
		}
		final Set<DTASelectProtein> proteins = psm.getProteins();
		for (final DTASelectProtein dtaSelectProtein : proteins) {
			final String proteinKey = dtaSelectProtein.getAccession();
			QuantifiedProteinInterface quantifiedProtein = null;
			if (StaticQuantMaps.proteinMap.containsKey(proteinKey)) {
				quantifiedProtein = StaticQuantMaps.proteinMap.getItem(proteinKey);
			} else {
				quantifiedProtein = new NonQuantifiedProtein(dtaSelectProtein, isIgnoreTaxonomies());
			}
			StaticQuantMaps.proteinMap.addItem(quantifiedProtein);
			// add psm to the proteins
			quantifiedProtein.addPSM(quantifiedPSM, true);
			// add protein to the psm
			quantifiedPSM.addQuantifiedProtein(quantifiedProtein, true);
			// add peptide to the protein
			quantifiedProtein.addPeptide(quantifiedPeptide, true);
			// add to the map (if it was already there
			// is not a problem, it will be only once)
			addToMap(proteinKey, proteinToPeptidesMap, KeyUtils.getSequenceKey(quantifiedPSM, true));
			// add protein to protein map
			localProteinMap.put(proteinKey, quantifiedProtein);
			// add to protein-experiment map
			addToMap(experimentKey, experimentToProteinsMap, proteinKey);

		}
	}
}
