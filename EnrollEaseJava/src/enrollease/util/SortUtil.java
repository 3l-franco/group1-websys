package enrollease.util;

import java.util.Comparator;

/**
 * Sorting algorithms written from scratch. Each one sorts an ARRAY in place
 * using any Comparator, so the same code can sort Subjects, Persons, etc.
 *
 * Concepts: Sort algorithms (bubble, selection, insertion, merge), Generic methods, Arrays.
 */
public class SortUtil {

    public static final String[] ALGORITHM_NAMES = {
        "Bubble Sort", "Selection Sort", "Insertion Sort", "Merge Sort"
    };

    // How many comparisons the last sort needed (to compare the algorithms).
    private static int comparisons = 0;

    public static int getComparisons() {
        return comparisons;
    }

    /** Sorts using the chosen algorithm (1 = bubble, 2 = selection, 3 = insertion, 4 = merge). */
    public static <T> void sort(T[] a, Comparator<? super T> c, int algorithm) {
        comparisons = 0;
        switch (algorithm) {
            case 1: bubbleSort(a, c); break;
            case 2: selectionSort(a, c); break;
            case 3: insertionSort(a, c); break;
            default: mergeSort(a, c); break;
        }
    }

    private static <T> int compare(T x, T y, Comparator<? super T> c) {
        comparisons++;
        return c.compare(x, y);
    }

    public static <T> void bubbleSort(T[] a, Comparator<? super T> c) {
        for (int pass = 0; pass < a.length - 1; pass++) {
            boolean swapped = false;
            for (int i = 0; i < a.length - 1 - pass; i++) {
                if (compare(a[i], a[i + 1], c) > 0) {
                    T temp = a[i];
                    a[i] = a[i + 1];
                    a[i + 1] = temp;
                    swapped = true;
                }
            }
            if (!swapped) {
                break; // already sorted
            }
        }
    }

    public static <T> void selectionSort(T[] a, Comparator<? super T> c) {
        for (int i = 0; i < a.length - 1; i++) {
            int min = i;
            for (int j = i + 1; j < a.length; j++) {
                if (compare(a[j], a[min], c) < 0) {
                    min = j;
                }
            }
            T temp = a[i];
            a[i] = a[min];
            a[min] = temp;
        }
    }

    public static <T> void insertionSort(T[] a, Comparator<? super T> c) {
        for (int i = 1; i < a.length; i++) {
            T key = a[i];
            int j = i - 1;
            while (j >= 0 && compare(a[j], key, c) > 0) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    public static <T> void mergeSort(T[] a, Comparator<? super T> c) {
        if (a.length < 2) {
            return;
        }
        T[] temp = a.clone();
        mergeSort(a, temp, 0, a.length - 1, c);
    }

    private static <T> void mergeSort(T[] a, T[] temp, int low, int high, Comparator<? super T> c) {
        if (low >= high) {
            return;
        }
        int mid = (low + high) / 2;
        mergeSort(a, temp, low, mid, c);        // sort left half
        mergeSort(a, temp, mid + 1, high, c);   // sort right half

        int i = low;
        int j = mid + 1;
        int k = low;
        while (i <= mid && j <= high) {         // merge the two halves
            if (compare(a[i], a[j], c) <= 0) {
                temp[k++] = a[i++];
            } else {
                temp[k++] = a[j++];
            }
        }
        while (i <= mid) {
            temp[k++] = a[i++];
        }
        while (j <= high) {
            temp[k++] = a[j++];
        }
        for (k = low; k <= high; k++) {
            a[k] = temp[k];
        }
    }
}
