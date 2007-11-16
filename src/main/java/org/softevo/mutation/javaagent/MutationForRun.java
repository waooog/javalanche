package org.softevo.mutation.javaagent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.softevo.mutation.io.Io;
import org.softevo.mutation.results.Mutation;
import org.softevo.mutation.results.persistence.HibernateUtil;
import org.softevo.mutation.results.persistence.QueryManager;

public class MutationForRun {

	private static Logger logger = Logger.getLogger(MutationForRun.class);

	/**
	 * SingletonHolder is loaded on the first execution of
	 * Singleton.getInstance() or the first access to SingletonHolder.INSTANCE,
	 * not before. see
	 * http://en.wikipedia.org/wiki/Initialization_on_demand_holder_idiom
	 */
	private static class SingletonHolder {
		private static MutationForRun INSTANCE = new MutationForRun();
	}

	private static final int MAX_MUTATIONS = getMaxMutations();

	private static final String MUTATIONS_PER_RUN_KEY = "mutationsPerRun";

	private static final boolean NON_RANDOM = true;

	private List<Mutation> mutations;

	private List<Mutation> appliedMutations = new ArrayList<Mutation>();

	public static MutationForRun getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private static int getMaxMutations() {
		String mutationsPerRun = System.getProperty(MUTATIONS_PER_RUN_KEY);
		if (mutationsPerRun != null) {
			int mutations = Integer.parseInt(mutationsPerRun);
			return mutations;
		}
		return 0;
	}

	private MutationForRun() {
		mutations = getMutationsForRun();
		logger.info("Applying " + mutations.size() + " mutations");
		for (Mutation m : mutations) {
			logger.info(m);
		}
	}

	public Collection<String> getClassNames() {
		Set<String> classNames = new HashSet<String>();
		for (Mutation m : mutations) {
			classNames.add(m.getClassName());
		}
		return classNames;
	}

	public List<Mutation> getMutations() {
		return Collections.unmodifiableList(mutations);
	}

	private static List<Mutation> getMutationsForRun() {
		if (System.getProperty("mutation.file") != null) {
			logger.info("Found Property mutation.file");
			String filename = System.getProperty("mutation.file");
			if (!filename.equals("")) {
				logger.info("Value of mutation file: " + filename);
				File file = new File(filename);
				if (file.exists()) {
					logger.info("Location of mutation.file: "
							+ file.getAbsolutePath());
					return getMutationsByFile(file);
				} else {
					logger.info("Mutation file does not exist " + file);
				}
			}
		} else {
			logger.info("Property not found: mutation.file");
			// throw new RuntimeException("property not found");
		}
		if (NON_RANDOM) {
			return getMutationsFromDB();
		}
		return null;
	}

	private static List<Mutation> getMutationsFromDB() {
		Session session = HibernateUtil.getSessionFactory().openSession();
		Transaction tx = session.beginTransaction();
		Query query = session
				.createSQLQuery(
						"SELECT m.* FROM Mutation m JOIN TestCoverageClassResult tccr ON m.classname = tccr.classname JOIN TestCoverageClassResult_TestCoverageLineResult AS class_line ON class_line.testcoverageclassresult_id = tccr.id JOIN TestCoverageLineResult AS tclr ON tclr.id = class_line.lineresults_id 	WHERE m.mutationresult_id IS NULL AND m.linenumber = tclr.linenumber")
				.addEntity(Mutation.class);

		query.setMaxResults(MAX_MUTATIONS);
		List results = query.list();
		List<Mutation> mutationList = new ArrayList<Mutation>();
		for (Object m : results) {
			Mutation mutation = (Mutation) m;
			// Query hqlQuery = session.createQuery("Mutation as m inner join
			// fetch m.mutationResult inner join fetch m.mutationResult.failures
			// inner join fetch m.mutationResult.errors inner join fetch
			// m.mutationResult.passing WHERE m.id = :id" );
			// hqlQuery.setLong("id", mutation.getId());
			// Mutation mutationToAdd = (Mutation) hqlQuery.uniqueResult();
			Mutation mutationToAdd = mutation;
			logger.info(mutationToAdd);
			mutationList.add(mutationToAdd);
		}
		tx.commit();
		session.close();
		return mutationList;
	}

	private static List<Mutation> getMutationsByFile(File file) {
		List<Long> idList = Io.getIDsFromFile(file);
		return QueryManager.getMutationsFromDbByID(idList.toArray(new Long[0]));
	}

	public void reinit() {
		mutations = getMutationsForRun();
		logger.info("Got " + mutations.size() + " mutations");
	}

	public boolean containsMutation(Mutation mutation) {
		boolean result = hasMutation(mutation);
		if (result) {
			logger.debug("mutation contained:  " + mutation);
		} else {
			logger.debug("mutation not contained:  " + mutation);
		}
		return result;
	}

	private boolean hasMutation(Mutation searchMutation) {
		if (searchMutation != null) {
			for (Mutation m : mutations) {
				if (searchMutation.equalsWithoutId(m)) {
					return true;
				}
			}
		}
		return false;
	}

	public static void mutationApplied(Mutation mutation) {
		getInstance()._mutationApplied(mutation);
	}

	private void _mutationApplied(Mutation mutation) {
		appliedMutations.add(mutation);
	}

	public void reportAppliedMutations() {
		List<Mutation> notApplied = new ArrayList<Mutation>();
		int applied = 0;
		for (Mutation m : mutations) {
			if (appliedMutations.contains(m)) {
				applied++;
			} else {
				notApplied.add(m);
			}
		}
		logger.info(applied + " Mutations out of " + mutations.size()
				+ " Where applied to bytecode");
		if (applied < mutations.size() || notApplied.size() > 0) {
			logger.error("Not all mutations where applied to source code");
			for (Mutation mutation : notApplied) {
				logger.warn("Mutation not applied " + mutation);
			}
		}
	}

	public static int getNumberOfAppliedMutations() {
		return getInstance().appliedMutations.size();
	}

	public static List<Mutation> getAppliedMutations() {
		return getInstance().appliedMutations;
	}
}
