package example;

import java.util.Collections;
import java.util.List;

public final class CollectionsSortIssues {

    public void sort(List<String> names) {
        Collections.sort(names);
    }

    public void sortCorrectly(List<String> names) {
        names.sort(null);
    }

    public void sortWithLocalMethod(List<String> names) {
        sort(names);
    }
}
