package com.amd.aparapi.internal.model;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;

import com.amd.aparapi.internal.model.ClassModel.ClassModelMatcher;

public class StringToModel implements Iterable<ClassModel> {
    private final HashMap<String, List<ClassModel>> mapping =
        new HashMap<String, List<ClassModel>>();

    public boolean isEmpty() {
        return mapping.keySet().isEmpty();
    }

    public void add(String name, ClassModel model) {
       if (!mapping.containsKey(name)) {
          mapping.put(name, new LinkedList<ClassModel>());
       }

       mapping.get(name).add(model);
    }

    public ClassModel get(String name, ClassModelMatcher matcher) {
        if (!mapping.containsKey(name)) return null;

        List<ClassModel> possibleMatches = mapping.get(name);
        if (possibleMatches.size() == 1) return possibleMatches.get(0);

        for (final ClassModel m : possibleMatches) {
            if (matcher.matches(m)) {
                return m;
            }
        }
        return null;
    }

    public List<ClassModel> getModels(String name) {
        if (mapping.containsKey(name)) {
            return mapping.get(name);
        } else {
            return null;
        }
    }

    @Override
    public Iterator<ClassModel> iterator() {
       return new Iterator<ClassModel>() {
           final Iterator<String> keyIter = mapping.keySet().iterator();
           Iterator<ClassModel> currValueIterator = null;

           @Override
           public boolean hasNext() {
               if (currValueIterator == null || !currValueIterator.hasNext()) {
                   return keyIter.hasNext();
               }

               return currValueIterator.hasNext();
           }

           @Override
           public ClassModel next() {
               if (currValueIterator == null || !currValueIterator.hasNext()) {
                   String curr = keyIter.next();
                   currValueIterator = mapping.get(curr).iterator();
               }
               return currValueIterator.next();
           }

           @Override
           public void remove() {
               throw new UnsupportedOperationException();
           }
       };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (Map.Entry<String, List<ClassModel>> entry : mapping.entrySet()) {
            sb.append("  { " + entry.getKey() + " => [ ");
            for (ClassModel model : entry.getValue()) {
                sb.append(model.toString() + " ");
            }
            sb.append(" }\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
