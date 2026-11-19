package com.github.wf.model.node;

import java.util.List;

/**
 * A single field in a user-task form. Fields are declared on the userTask node
 * and rendered by the monitor UI; required fields are enforced server-side on
 * task completion.
 */
public class FormField {
    /** Variable name written into the process instance on completion. */
    private final String key;
    /** Human-readable label shown in the form. */
    private final String label;
    /** text | textarea | number | select | date. */
    private final String type;
    /** Whether a non-empty value must be supplied to complete the task. */
    private final boolean required;
    /** Allowed values for select type. */
    private final List<String> options;
    /** Placeholder hint shown in the input. */
    private final String placeholder;

    public FormField(String key, String label, String type, boolean required,
                     List<String> options, String placeholder) {
        this.key = key;
        this.label = label;
        this.type = type != null ? type : "text";
        this.required = required;
        this.options = options != null ? List.copyOf(options) : List.of();
        this.placeholder = placeholder;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getType() { return type; }
    public boolean isRequired() { return required; }
    public List<String> getOptions() { return options; }
    public String getPlaceholder() { return placeholder; }
}
