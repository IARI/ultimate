/*
 * Copyright (C) 2013-2015 Christian Schilling (schillic@informatik.uni-freiburg.de)
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2013-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 * 
 * This file is part of the ULTIMATE Core.
 * 
 * The ULTIMATE Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Core. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Core, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Core grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.core.model.models;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.core.model.models.annotation.IAnnotations;

/**
 * Helper methods for Ultimate models.
 * 
 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
 * @author Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * 
 */
public final class ModelUtils {
	/**
	 * Takes annotations from one {@link IElement} (if any) and adds them to another {@link IElement}. This is a shallow
	 * copy.
	 * 
	 * @param oldE
	 *            old {@link IElement} to take annotations from.
	 * @param newE
	 *            new {@link IElement} to add annotations to.
	 */
	public static void copyAnnotations(final IElement oldE, final IElement newE) {
		if (oldE == null || newE == null) {
			return;
		}
		if (!oldE.hasPayload()) {
			return;
		}
		final IPayload oldPayload = oldE.getPayload();
		if (oldPayload.hasAnnotation()) {
			newE.getPayload().getAnnotations().putAll(oldPayload.getAnnotations());
		}
	}

	/**
	 * Collects all annotations annotated to a collection of {@link IElement}s and annotates them to a new
	 * {@link IElement}.
	 * 
	 * Throws an exception if annotations would be lost.
	 * 
	 * @param oldElem
	 *            a collection of {@link IElement}s
	 * @param newElem
	 *            the IElement to which the annotations should be annotated
	 */
	public static void mergeAnnotations(final Collection<? extends IElement> oldElem, final IElement newElem) {
		if (oldElem == null || newElem == null) {
			return;
		}

		final List<Entry<String, IAnnotations>> oldElemAnnots =
				oldElem.stream().filter(IElement::hasPayload).map(IElement::getPayload).filter(IPayload::hasAnnotation)
						.flatMap(a -> a.getAnnotations().entrySet().stream()).collect(Collectors.toList());
		final Map<String, IAnnotations> newElemAnnots = newElem.getPayload().getAnnotations();
		for (final Entry<String, IAnnotations> oldElemAnnot : oldElemAnnots) {
			final IAnnotations removedNewElemAnnot = newElemAnnots.put(oldElemAnnot.getKey(), oldElemAnnot.getValue());
			if (removedNewElemAnnot != null) {
				throw new UnsupportedOperationException("Annotations would be lost: " + oldElemAnnot.getKey());
			}
		}

	}

	/**
	 * Takes annotations from one {@link IElement} that are assignable from <code>annotation</code> and adds them to
	 * another {@link IElement}. This is a shallow copy.
	 * 
	 * @param oldE
	 *            old {@link IElement} to take annotations from
	 * @param newE
	 *            new {@link IElement} to add annotations to
	 */
	public static <E extends IAnnotations> void copyAnnotations(final IElement oldE, final IElement newE,
			final Class<E> annotation) {
		if (oldE == null || newE == null || annotation == null) {
			return;
		}
		if (!oldE.hasPayload()) {
			return;
		}
		final IPayload oldPayload = oldE.getPayload();
		if (oldPayload.hasAnnotation()) {
			final Map<String, IAnnotations> oldAnnots = oldPayload.getAnnotations();
			final Collection<Entry<String, IAnnotations>> toMerge = new ArrayList<>();
			for (final Entry<String, IAnnotations> entry : oldAnnots.entrySet()) {
				if (annotation.isAssignableFrom(entry.getValue().getClass())) {
					toMerge.add(entry);
				}
			}
			if (toMerge.isEmpty()) {
				return;
			}
			final Map<String, IAnnotations> newAnnots = newE.getPayload().getAnnotations();
			toMerge.forEach(entry -> newAnnots.put(entry.getKey(), entry.getValue()));
		}
	}
}
