package pe.edu.utp.university_system.course;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.university_system.ai.AIRecommendationEligibility;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SyllabusPdfImportService {

    private static final int TOTAL_WEEKS = 18;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Pattern FORMULA_ITEM = Pattern.compile(
            "\\((\\d+(?:[.,]\\d+)?)%\\)\\s*([A-Z][A-Z0-9_-]{0,15})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIT_START = Pattern.compile(
            "^UNIDAD DE APRENDIZAJE\\s+\\d+.*?SEMANA\\s+(1[0-8]|[1-9])(?:\\D|$)");

    public SyllabusPdfImportResponse importPdf(MultipartFile file) {
        validateFile(file);

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.isEncrypted()) {
                throw badRequest("El PDF tiene contraseña y no puede ser leído.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                throw badRequest("El PDF no contiene texto seleccionable. Convierte el documento con OCR antes de importarlo.");
            }

            return parseSyllabus(text);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            throw badRequest("No se pudo leer el PDF. Verifica que el archivo no esté dañado.");
        }
    }

    SyllabusPdfImportResponse parseSyllabus(String text) {
        String normalizedText = normalizeForMatching(text);
        int scheduleIndex = normalizedText.indexOf("CRONOGRAMA DE ACTIVIDADES");
        if (scheduleIndex < 0) {
            throw badRequest("No se encontró la sección 'Cronograma de actividades' en el PDF.");
        }

        List<ModuleRegistrationRequest> modules = emptyModules();
        List<String> warnings = new ArrayList<>();
        Map<String, Double> weights = parseWeights(normalizedText);
        List<EvaluationRow> evaluations = parseEvaluationRows(text, weights);
        List<UnitPlan> unitPlans = parseUnitPlans(text.substring(0, Math.min(scheduleIndex, text.length())));
        int maximumSession = parseMaximumSession(text.substring(Math.min(scheduleIndex, text.length())));
        assignUnitTopics(unitPlans, evaluations, maximumSession, modules, warnings);
        populateEvaluationWeekTopics(modules, evaluations);
        addEvaluations(modules, evaluations);

        List<Integer> weeksWithoutContent = modules.stream()
                .filter(module -> module.getTopics().isEmpty() && module.getEvaluations().isEmpty())
                .map(ModuleRegistrationRequest::getModuleNumber)
                .toList();
        if (!weeksWithoutContent.isEmpty()) {
            warnings.add("Revisa las semanas sin contenido reconocido: " + weeksWithoutContent + ".");
        }

        long topicCount = modules.stream().mapToLong(module -> module.getTopics().size()).sum();
        if (topicCount == 0) {
            throw badRequest("Se encontró el cronograma, pero no fue posible reconocer sus temas.");
        }

        double totalWeight = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(totalWeight - 100.0) > 0.001) {
            throw badRequest("Los porcentajes reconocidos suman " + totalWeight + "% y deben sumar 100%.");
        }

        return new SyllabusPdfImportResponse(modules, totalWeight, warnings);
    }

    private List<UnitPlan> parseUnitPlans(String unitsText) {
        List<UnitPlan> units = new ArrayList<>();
        UnitPlan currentUnit = null;
        boolean insideTemario = false;

        for (String rawLine : unitsText.split("\\R")) {
            String line = cleanLine(rawLine);
            String normalized = normalizeForMatching(line);
            Matcher unitMatcher = UNIT_START.matcher(normalized);
            if (unitMatcher.find()) {
                currentUnit = new UnitPlan(Integer.parseInt(unitMatcher.group(1)), new ArrayList<>());
                units.add(currentUnit);
                insideTemario = false;
                continue;
            }
            if (normalized.equals("TEMARIO:")) {
                insideTemario = true;
                continue;
            }
            if (normalized.matches("6[.]?\\s+METODOLOGIA.*")) {
                insideTemario = false;
                continue;
            }
            if (insideTemario && currentUnit != null && !line.isBlank()) {
                String topicPart = cleanTopic(line);
                addUnitTopic(currentUnit, topicPart);
            }
        }
        if (units.isEmpty()) {
            throw badRequest("No se reconocieron las unidades de aprendizaje del sílabo.");
        }
        return units;
    }

    private void addUnitTopic(UnitPlan unit, String topic) {
        if (!topic.isBlank() && !unit.topics().contains(topic)) {
            unit.topics().add(topic);
        }
    }

    private void assignUnitTopics(List<UnitPlan> units, List<EvaluationRow> evaluations,
                                  int maximumSession,
                                  List<ModuleRegistrationRequest> modules, List<String> warnings) {
        int sessionsPerWeek = Math.max(1, (int) Math.ceil(maximumSession / (double) TOTAL_WEEKS));
        for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
            UnitPlan unit = units.get(unitIndex);
            int endWeek = unitIndex + 1 < units.size()
                    ? units.get(unitIndex + 1).startWeek() - 1
                    : TOTAL_WEEKS;
            List<Integer> capacities = new ArrayList<>();
            int totalCapacity = 0;
            for (int week = unit.startWeek(); week <= endWeek; week++) {
                int remainingSessions = maximumSession - ((week - 1) * sessionsPerWeek);
                int capacity = Math.max(0, Math.min(sessionsPerWeek, remainingSessions));
                capacities.add(capacity);
                totalCapacity += capacity;
            }

            List<String> topics = normalizeUnitTopics(unit.topics(), totalCapacity);
            if (topics.size() > totalCapacity) {
                throw badRequest("La unidad que inicia en la semana " + unit.startWeek()
                        + " contiene más temas que sesiones disponibles.");
            }

            int spareSessions = totalCapacity - topics.size();
            List<Integer> evaluationSessionCandidates = evaluations.stream()
                    .filter(evaluation -> !evaluation.isParticipation())
                    .map(EvaluationRow::week)
                    .filter(week -> week >= unit.startWeek() && week <= endWeek)
                    .sorted(java.util.Comparator.reverseOrder())
                    .toList();

            for (int evaluationWeek : evaluationSessionCandidates) {
                if (spareSessions <= 0) break;
                int capacityIndex = evaluationWeek - unit.startWeek();
                if (capacities.get(capacityIndex) > 0) {
                    capacities.set(capacityIndex, capacities.get(capacityIndex) - 1);
                    spareSessions--;
                }
            }

            int[] topicsPerWeek = new int[capacities.size()];
            int remainingTopics = topics.size();
            for (int index = capacities.size() - 1; index >= 0; index--) {
                int earlierWeeksWithCapacity = 0;
                for (int earlier = 0; earlier < index; earlier++) {
                    if (capacities.get(earlier) > 0) earlierWeeksWithCapacity++;
                }
                int assignable = Math.max(0, remainingTopics - earlierWeeksWithCapacity);
                topicsPerWeek[index] = Math.min(capacities.get(index), assignable);
                remainingTopics -= topicsPerWeek[index];
            }
            if (remainingTopics > 0) {
                throw badRequest("No fue posible ubicar todos los temas de la unidad que inicia en la semana "
                        + unit.startWeek() + ".");
            }

            int topicIndex = 0;
            for (int index = 0; index < topicsPerWeek.length; index++) {
                int week = unit.startWeek() + index;
                for (int count = 0; count < topicsPerWeek[index]; count++) {
                    addTopic(modules, week, topics.get(topicIndex++));
                }
            }
        }
        warnings.add("Los temas fueron distribuidos por semana y sesión; las evaluaciones conservan los temas correspondientes.");
    }

    private List<String> normalizeUnitTopics(List<String> rawTopics, int maximumTopics) {
        if (rawTopics.size() <= maximumTopics) return rawTopics;

        List<String> groupedTopics = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        for (String line : rawTopics) {
            String normalized = normalizeForMatching(line);
            if (normalized.matches("SESION INTEGRADORA\\s*\\d*[.]?")) {
                if (!pending.isEmpty()) {
                    groupedTopics.add(pending.toString().trim());
                    pending.setLength(0);
                }
                groupedTopics.add(line);
                continue;
            }
            if (!pending.isEmpty()) pending.append(' ');
            pending.append(line);
            if (line.endsWith(".")) {
                groupedTopics.add(pending.toString().trim());
                pending.setLength(0);
            }
        }
        if (!pending.isEmpty()) groupedTopics.add(pending.toString().trim());
        return groupedTopics.size() <= maximumTopics ? groupedTopics : rawTopics;
    }

    private int parseMaximumSession(String scheduleText) {
        int maximum = 0;
        // PDFBox puede dejar el número de sesión solo o seguido por el texto
        // de otra columna de la misma fila (por ejemplo: "35 Evaluación").
        Matcher matcher = Pattern.compile("(?m)^\\s*(\\d{1,2})(?:\\s+|$)").matcher(scheduleText);
        while (matcher.find()) {
            int candidate = Integer.parseInt(matcher.group(1));
            if (candidate <= TOTAL_WEEKS * 3) {
                maximum = Math.max(maximum, candidate);
            }
        }
        // Algunos formatos antiguos solo muestran las semanas. En ese caso se
        // conserva el comportamiento previo de una sesión por semana.
        return maximum == 0 ? TOTAL_WEEKS : maximum;
    }

    private void populateEvaluationWeekTopics(List<ModuleRegistrationRequest> modules,
                                              List<EvaluationRow> evaluations) {
        List<Integer> mainEvaluationWeeks = evaluations.stream()
                .filter(evaluation -> !evaluation.isParticipation())
                .map(EvaluationRow::week)
                .sorted()
                .toList();

        int previousMainEvaluationWeek = 0;
        for (int evaluationWeek : mainEvaluationWeeks) {
            copyTopicsToEvaluationWeek(
                    modules,
                    previousMainEvaluationWeek + 1,
                    evaluationWeek - 1,
                    evaluationWeek);
            previousMainEvaluationWeek = evaluationWeek;
        }

        evaluations.stream()
                .filter(EvaluationRow::isParticipation)
                .forEach(participation -> {
            int participationWeek = participation.week();
            int previousEvaluationWeek = mainEvaluationWeeks.stream()
                    .filter(week -> week < participationWeek)
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
            copyTopicsToEvaluationWeek(
                    modules,
                    previousEvaluationWeek + 1,
                    participationWeek - 1,
                    participationWeek);
        });
    }

    private void copyTopicsToEvaluationWeek(List<ModuleRegistrationRequest> modules,
                                            int firstWeek,
                                            int lastWeek,
                                            int evaluationWeek) {
        List<String> collectedTopics = new ArrayList<>();
        for (int week = firstWeek; week <= lastWeek; week++) {
            for (String topic : modules.get(week - 1).getTopics()) {
                if (!collectedTopics.contains(topic)) collectedTopics.add(topic);
            }
        }

        List<String> evaluationWeekTopics = modules.get(evaluationWeek - 1).getTopics();
        for (String topic : evaluationWeekTopics) {
            if (!collectedTopics.contains(topic)) collectedTopics.add(topic);
        }
        evaluationWeekTopics.clear();
        evaluationWeekTopics.addAll(collectedTopics);
    }

    private Map<String, Double> parseWeights(String normalizedText) {
        Map<String, Double> weights = new LinkedHashMap<>();
        Matcher matcher = FORMULA_ITEM.matcher(normalizedText);
        while (matcher.find()) {
            weights.put(matcher.group(2).toUpperCase(Locale.ROOT),
                    Double.parseDouble(matcher.group(1).replace(',', '.')));
        }
        if (weights.isEmpty()) {
            throw badRequest("No se reconocieron evaluaciones ni porcentajes en el sistema de evaluación.");
        }
        return weights;
    }

    private List<EvaluationRow> parseEvaluationRows(String originalText, Map<String, Double> weights) {
        String compact = originalText.replaceAll("\\s+", " ");
        String normalizedCompact = normalizeForMatching(compact);
        int tableStart = normalizedCompact.indexOf("TIPO DESCRIPCION SEMANA OBSERVACION");
        int tableEnd = normalizedCompact.indexOf("INDICACIONES SOBRE FORMULAS", tableStart);
        if (tableStart < 0 || tableEnd < 0) {
            throw badRequest("No se encontró la tabla de evaluaciones del sílabo.");
        }
        String evaluationTable = compact.substring(tableStart, tableEnd);
        List<EvaluationRow> evaluations = new ArrayList<>();
        for (Map.Entry<String, Double> weight : weights.entrySet()) {
            String code = weight.getKey();
            Pattern rowPattern = Pattern.compile("(?:^|\\s)" + Pattern.quote(code)
                    + "\\s+(.+?)\\s+(1[0-8]|[1-9])\\s+(?:EVALUACIÓN\\s+)?"
                    + "(?:INDIVIDUAL|GRUPAL)(?=\\s|$)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher rowMatcher = rowPattern.matcher(evaluationTable);
            if (!rowMatcher.find()) {
                throw badRequest("No se encontró la semana correspondiente a " + code + ".");
            }
            String title = toDisplayTitle(rowMatcher.group(1));
            int week = Integer.parseInt(rowMatcher.group(2));
            EvaluationType type = resolveEvaluationType(code, title);
            evaluations.add(new EvaluationRow(code, title, week, weight.getValue(), type));
        }
        return evaluations;
    }

    private EvaluationType resolveEvaluationType(String code, String title) {
        String normalizedTitle = normalizeForMatching(title);
        return switch (code) {
            case "PC1", "PC" -> EvaluationType.PC_1;
            case "PC2" -> EvaluationType.PC_2;
            case "TA" -> EvaluationType.ACADEMIC_TASK;
            case "EXPA" -> EvaluationType.PARCIAL_EXAM;
            case "EXFN" -> EvaluationType.FINAL_EXAM;
            case "PA" -> EvaluationType.CLASS_PARTICIPATION;
            default -> {
                if (normalizedTitle.contains("PARTICIPACION")) yield EvaluationType.CLASS_PARTICIPATION;
                if (normalizedTitle.contains("TAREA ACADEMICA")) yield EvaluationType.ACADEMIC_TASK;
                if (normalizedTitle.contains("AVANCE")
                        && (normalizedTitle.contains("PORTAFOLIO") || normalizedTitle.contains("PROYECTO"))) {
                    yield EvaluationType.PORTFOLIO_PROJECT_ADVANCE;
                }
                if ((normalizedTitle.contains("PORTAFOLIO") || normalizedTitle.contains("PROYECTO"))
                        && normalizedTitle.contains("FINAL")) {
                    yield EvaluationType.FINAL_PORTFOLIO_PROJECT;
                }
                if (normalizedTitle.contains("EXAMEN PARCIAL")) yield EvaluationType.PARCIAL_EXAM;
                if (normalizedTitle.contains("EXAMEN FINAL")) yield EvaluationType.FINAL_EXAM;
                yield EvaluationType.OTHER;
            }
        };
    }

    private String toDisplayTitle(String normalizedTitle) {
        String lower = normalizedTitle.toLowerCase(Locale.forLanguageTag("es-PE"));
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void addEvaluations(List<ModuleRegistrationRequest> modules,
                                List<EvaluationRow> evaluations) {
        for (EvaluationRow evaluation : evaluations) {
            addEvaluation(modules, evaluation.week(), evaluation.title(), evaluation.weight(), evaluation.type());
        }
    }

    private void addEvaluation(List<ModuleRegistrationRequest> modules, int week, String title,
                               double weight, EvaluationType type) {
        EvaluationRegistrationRequest evaluation = new EvaluationRegistrationRequest();
        evaluation.setTitle(title);
        evaluation.setWeight(weight);
        evaluation.setEvaluationType(type);
        evaluation.setAiFeedbackEnabled(AIRecommendationEligibility.defaultEnabled(type, title));
        modules.get(week - 1).getEvaluations().add(evaluation);
    }

    private void addTopic(List<ModuleRegistrationRequest> modules, int week, String topic) {
        if (topic.isBlank()) {
            return;
        }
        List<String> topics = modules.get(week - 1).getTopics();
        if (!topics.contains(topic)) {
            topics.add(topic);
        }
    }

    private String cleanTopic(String value) {
        String topic = cleanLine(value);
        int bulletIndex = topic.indexOf('•');
        if (bulletIndex < 0) bulletIndex = topic.indexOf('');
        if (bulletIndex >= 0) topic = topic.substring(0, bulletIndex).trim();
        return topic;
    }

    private String cleanLine(String value) {
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private List<ModuleRegistrationRequest> emptyModules() {
        List<ModuleRegistrationRequest> modules = new ArrayList<>();
        for (int week = 1; week <= TOTAL_WEEKS; week++) {
            ModuleRegistrationRequest module = new ModuleRegistrationRequest();
            module.setModuleNumber(week);
            module.setTopics(new ArrayList<>());
            module.setEvaluations(new ArrayList<>());
            modules.add(module);
        }
        return modules;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Selecciona un archivo PDF.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("El PDF supera el tamaño máximo de 10 MB.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw badRequest("El archivo seleccionado debe tener extensión .pdf.");
        }
    }

    private String normalizeForMatching(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record UnitPlan(int startWeek, List<String> topics) {
    }

    private record EvaluationRow(String code, String title, int week, double weight, EvaluationType type) {
        boolean isParticipation() {
            return type == EvaluationType.CLASS_PARTICIPATION;
        }
    }
}
