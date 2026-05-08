package com.inbook.controller;


import com.inbook.repository.entity.AppUser;
import com.inbook.repository.entity.SchoolClass;
import com.inbook.repository.entity.Subject;
import com.inbook.service.SubjectService;
import org.springframework.data.domain.Page;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.lang.reflect.Method;
import java.security.Principal;

@Controller
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    private boolean isAdminUser(AppUser user) {
        if (user == null) return false;
        try {
            Object rolesObj = user.getRoles();
            if (rolesObj == null) return false;

            String up = String.valueOf(rolesObj).toUpperCase();

            return up.contains("TYPE_ADMIN");
        } catch (Exception ignore) {
        }
        return false;
    }

    private static Object tryInvoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String asTrimmedString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    private static String buildClasseLabel(Object classe) {
        if (classe == null) return null;

        // Try common getter names (keep reflection-based to avoid compilation issues if a getter doesn't exist)
        String nome = asTrimmedString(tryInvoke(classe, "getNome"));
        String sezione = asTrimmedString(tryInvoke(classe, "getSezione"));
        String annoScolastico = asTrimmedString(tryInvoke(classe, "getAnnoScolastico"));
        if (annoScolastico == null) {
            annoScolastico = asTrimmedString(tryInvoke(classe, "getAnno_scolastico"));
        }
        if (annoScolastico == null) {
            annoScolastico = asTrimmedString(tryInvoke(classe, "getAnno"));
        }

        // Compose label: e.g. "3A 2025/26 A" depending on which parts exist
        StringBuilder sb = new StringBuilder();
        if (nome != null) sb.append(nome);
        if (annoScolastico != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(annoScolastico);
        }
        if (sezione != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(sezione);
        }
        String label = sb.toString().trim();
        return label.isBlank() ? null : label;
    }

    @GetMapping("/subjects")
    public String subjectManager(Model model, Principal principal) {
        AppUser user = subjectService.requireLoggedUser(principal);
        boolean canManage = isAdminUser(user);
        model.addAttribute("canManageClasses", canManage);
        model.addAttribute("username", user.getUsername());
        return "subjectManager";
    }

    @PostMapping("/subjects/add")
    public String addsubjects(@RequestParam("classeId") Long classeId,
                              @RequestParam("nomeMateria") String nomeMateria,
                              Principal principal) {
        SchoolClass classe = subjectService.requireClass(classeId);
        AppUser docente = subjectService.requireLoggedUser(principal);
        subjectService.loadMateria(classe, docente, nomeMateria, null, null);
        return "redirect:/subjects?classeId=" + classeId;
    }

    @PostMapping("/subjects/edit")
    public String editSubject(@RequestParam("id") Long id,
                              @RequestParam("classeId") Long classeId,
                              @RequestParam("nomeMateria") String nomeMateria,
                              Principal principal) {
        SchoolClass classe = subjectService.requireClass(classeId);
        AppUser docente = subjectService.requireLoggedUser(principal);
        subjectService.modifySubject(id, classe, docente, nomeMateria, null);
        return "redirect:/subjects?classeId=" + classeId;
    }

    @PostMapping("/subjects/delete")
    public String deleteSubject(@RequestParam("id") Long id,
                                @RequestParam(name = "classeId", required = false) Long classeId) {
        try {
            subjectService.deleteSubject(id);
            if (classeId != null) {
                return "redirect:/subjects?classeId=" + classeId;
            }
            return "redirect:/subjects";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/subjects/book")
    public String associateBook(@RequestParam("subjectId") Long subjectId,
                                @RequestParam(name = "classeId", required = false) Long classeId,
                                @RequestParam("isbn") String isbn,
                                @RequestParam("autore") String autore,
                                @RequestParam("titolo") String titolo,
                                @RequestParam("volume") int volume,
                                @RequestParam("casaEditrice") String casaEditrice,
                                @RequestParam("prezzo") double prezzo,
                                @RequestParam("daAcquistare") boolean daAcquistare,
                                @RequestParam("consigliato") boolean consigliato) {
        subjectService.associateBook(
                subjectId,
                isbn,
                autore,
                titolo,
                volume,
                casaEditrice,
                prezzo,
                daAcquistare,
                consigliato
        );
        if (classeId != null) {
            return "redirect:/subjects?classeId=" + classeId;
        }
        return "redirect:/subjects";
    }

    @GetMapping("/materia-data")
    @ResponseBody
    public Map<String, Object> viewSubjects(@RequestParam(name = "classeId", required = false) Long classeId,
                                            @RequestParam(name = "page", defaultValue = "0") int page,
                                            @RequestParam(name = "size", defaultValue = "25") int size,
                                            @RequestParam(name = "search", required = false) String search,
                                            @RequestParam(name = "bookStatus", required = false) String bookStatus) {
        try {
            System.out.println("DEBUG: Fetching subjects from service...");
            Page<Subject> subjects = subjectService.getSubjectsPage(classeId, search, bookStatus, page, size);
            System.out.println("DEBUG: Found " + subjects.getTotalElements() + " subjects");

            List<Map<String, Object>> items = new ArrayList<>();
            if (subjects.hasContent()) {
                for (Subject s : subjects.getContent()) {
                    try {
                        items.add(toSubjectMap(s));
                    } catch (Exception rowEx) {
                        System.err.println("DEBUG: Error processing subject ID " + (s != null ? s.getId() : "null") + ": " + rowEx.getMessage());
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("items", items);
            response.put("page", subjects.getNumber());
            response.put("size", subjects.getSize());
            response.put("totalItems", subjects.getTotalElements());
            response.put("totalPages", subjects.getTotalPages());

            if (classeId != null) {
                SchoolClass classe = subjectService.requireClass(classeId);
                response.put("classeId", classe.getId());
                response.put("classeLabel", buildClasseLabel(classe));
                response.put("classeAnnoScolastico", asTrimmedString(tryInvoke(classe, "getAnno")));
                response.put("classeSezione", asTrimmedString(tryInvoke(classe, "getSezione")));
            }

            return response;
        } catch (Exception e) {
            System.err.println("DEBUG FATAL in viewSubjects: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Errore recupero dati: " + e.getMessage());
        }
    }

    private Map<String, Object> toSubjectMap(Subject s) {
        Map<String, Object> map = new HashMap<>();

        map.put("id", s.getId());
        map.put("classe_id", (s.getClasse() != null ? s.getClasse().getId() : null));

        if (s.getClasse() != null) {
            String classeNome = asTrimmedString(tryInvoke(s.getClasse(), "getNome"));
            String classeSezione = asTrimmedString(tryInvoke(s.getClasse(), "getSezione"));
            String classeAnnoScolastico = asTrimmedString(tryInvoke(s.getClasse(), "getAnnoScolastico"));
            if (classeAnnoScolastico == null) {
                classeAnnoScolastico = asTrimmedString(tryInvoke(s.getClasse(), "getAnno_scolastico"));
            }
            if (classeAnnoScolastico == null) {
                classeAnnoScolastico = asTrimmedString(tryInvoke(s.getClasse(), "getAnno"));
            }

            if (classeNome != null) map.put("classe_nome", classeNome);
            if (classeSezione != null) map.put("classe_sezione", classeSezione);
            if (classeAnnoScolastico != null) map.put("classe_anno_scolastico", classeAnnoScolastico);

            String classeLabel = buildClasseLabel(s.getClasse());
            if (classeLabel != null) map.put("classe_label", classeLabel);
        }

        map.put("docente_id", (s.getDocente() != null ? s.getDocente().getId() : null));
        if (s.getDocente() != null) {
            String name = s.getDocente().getName();
            String surname = s.getDocente().getSurname();
            String fullName = (name != null ? name : "").trim();
            if (surname != null && !surname.isBlank()) {
                fullName = (fullName.isEmpty() ? surname.trim() : (fullName + " " + surname.trim()));
            }
            if (!fullName.isBlank()) {
                map.put("docente_nome", fullName);
            }
        }

        map.put("nome_materia", s.getNomeMateria());

        map.put("has_book", s.getBook() != null);
        if (s.getBook() != null) {
            map.put("book_id", s.getBook().getIsbn());
            map.put("book_db_id", s.getBook().getId());
            map.put("book_isbn", s.getBook().getIsbn());
            map.put("book_autore", s.getBook().getAutore());
            map.put("book_titolo", s.getBook().getTitolo());
            map.put("book_volume", s.getBook().getVolume());
            map.put("book_casa_editrice", s.getBook().getCasaEditrice());
            map.put("book_prezzo", s.getBook().getPrezzo());
            map.put("book_da_acquistare", s.getBook().isDaAcquistare());
            map.put("book_consigliato", s.getBook().isConsigliato());
        }

        map.put("created_at", s.getCreated_at());
        map.put("updated_at", s.getUpdated_at());

        return map;
    }
}
