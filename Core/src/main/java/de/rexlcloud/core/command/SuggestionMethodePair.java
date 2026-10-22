package de.rexlcloud.core.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class SuggestionMethodePair {

    private Suggestion suggestion;
    private Method method;
    private Object commandInstance;

    public List<String> getCompletion() {
        try {
            return (List<String>) method.invoke(commandInstance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

}
