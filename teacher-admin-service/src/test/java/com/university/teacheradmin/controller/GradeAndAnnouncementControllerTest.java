package com.university.teacheradmin.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.university.teacheradmin.dto.AnnouncementCreateRequest;
import com.university.teacheradmin.dto.GradeCreateRequest;
import com.university.teacheradmin.exception.GlobalExceptionHandler;
import com.university.teacheradmin.model.Announcement;
import com.university.teacheradmin.model.Course;
import com.university.teacheradmin.model.Grade;
import com.university.teacheradmin.repository.AnnouncementRepository;
import com.university.teacheradmin.repository.CourseRepository;
import com.university.teacheradmin.repository.GradeRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Tests unitaires — GradeController + AnnouncementController
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GradeController + AnnouncementController — Tests unitaires")
class GradeAndAnnouncementControllerTest {

    // ── Mocks partagés ─────────────────────────────────────────────────────────
    @Mock private GradeRepository gradeRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private AnnouncementRepository announcementRepository;

    @InjectMocks private GradeController gradeController;
    @InjectMocks private AnnouncementController announcementController;

    private MockMvc gradeMvc;
    private MockMvc announceMvc;
    private final JsonMapper mapper = JsonMapper.builder().build();

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Course buildCourse(Long id) {
        Course c = new Course("Test", "Desc", 30);
        try {
            var f = Course.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, id);
            var ca = Course.class.getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(c, OffsetDateTime.now());
        } catch (Exception e) { throw new RuntimeException(e); }
        return c;
    }

    private Grade buildGrade(Long id, Long studentId, Long courseId, double value) {
        Grade g = new Grade(studentId, courseId, BigDecimal.valueOf(value));
        try {
            var f = Grade.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(g, id);
            var ca = Grade.class.getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(g, OffsetDateTime.now());
        } catch (Exception e) { throw new RuntimeException(e); }
        return g;
    }

    private Announcement buildAnnouncement(Long id, Long courseId, String text) {
        Announcement a = new Announcement(courseId, text);
        try {
            var f = Announcement.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(a, id);
            var ca = Announcement.class.getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(a, OffsetDateTime.now()); // corrigé : cible "a", pas le Field "ca"
        } catch (Exception e) {
            throw new RuntimeException(e); // ne jamais avaler silencieusement, comme les autres helpers
        }
        return a;
    }

    @BeforeEach
    void setUp() {
        gradeMvc = MockMvcBuilders
            .standaloneSetup(gradeController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        announceMvc = MockMvcBuilders
            .standaloneSetup(announcementController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTES — POST /grades
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /grades — Saisie d'une note")
    class SubmitGrade {

        private GradeCreateRequest validReq() {
            GradeCreateRequest r = new GradeCreateRequest();
            r.setStudentId(1L);
            r.setCourseId(1L);
            r.setValue(BigDecimal.valueOf(14.5));
            return r;
        }

        @Test
        @DisplayName("Note valide → 201")
        void validGrade_returns201() throws Exception {
            when(courseRepository.existsById(1L)).thenReturn(true);
            when(gradeRepository.findByStudentIdAndCourseId(1L, 1L))
                .thenReturn(Optional.empty());
            when(gradeRepository.save(any())).thenAnswer(inv -> {
                Grade g = inv.getArgument(0);
                return buildGrade(1L, g.getStudentId(), g.getCourseId(), g.getValue().doubleValue());
            });

            gradeMvc.perform(post("/grades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(validReq())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(14.5));
        }

        @Test
        @DisplayName("Mise à jour d'une note existante → 201 (upsert)")
        void updateExistingGrade_returns201() throws Exception {
            Grade existing = buildGrade(1L, 1L, 1L, 10.0);
            when(courseRepository.existsById(1L)).thenReturn(true);
            when(gradeRepository.findByStudentIdAndCourseId(1L, 1L))
                .thenReturn(Optional.of(existing));
            when(gradeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            GradeCreateRequest req = new GradeCreateRequest();
            req.setStudentId(1L);
            req.setCourseId(1L);
            req.setValue(BigDecimal.valueOf(16.0));

            gradeMvc.perform(post("/grades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(16.0));
        }

        @Test
        @DisplayName("Cours inexistant → 404")
        void nonExistentCourse_returns404() throws Exception {
            when(courseRepository.existsById(99L)).thenReturn(false);

            GradeCreateRequest req = new GradeCreateRequest();
            req.setStudentId(1L);
            req.setCourseId(99L);
            req.setValue(BigDecimal.valueOf(12.0));

            gradeMvc.perform(post("/grades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Note négative → 400")
        void negativeValue_returns400() throws Exception {
            GradeCreateRequest req = new GradeCreateRequest();
            req.setStudentId(1L);
            req.setCourseId(1L);
            req.setValue(BigDecimal.valueOf(-1.0));

            gradeMvc.perform(post("/grades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Note > 20 → 400")
        void valueTooHigh_returns400() throws Exception {
            GradeCreateRequest req = new GradeCreateRequest();
            req.setStudentId(1L);
            req.setCourseId(1L);
            req.setValue(BigDecimal.valueOf(20.01));

            gradeMvc.perform(post("/grades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("studentId manquant → 400")
        void missingStudentId_returns400() throws Exception {
            GradeCreateRequest req = new GradeCreateRequest();
            req.setCourseId(1L);
            req.setValue(BigDecimal.valueOf(12.0));

            gradeMvc.perform(post("/grades")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // NOTES — GET /grades?studentId=<id>
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /grades — Liste des notes")
    class ListGrades {

        @Test
        @DisplayName("Sans filtre → toutes les notes")
        void noFilter_returnsAll() throws Exception {
            when(gradeRepository.findAll()).thenReturn(List.of(
                buildGrade(1L, 1L, 1L, 14.0),
                buildGrade(2L, 2L, 1L, 10.0)
            ));

            gradeMvc.perform(get("/grades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("Filtre par studentId → notes de l'étudiant uniquement")
        void filterByStudentId_returnsStudentGrades() throws Exception {
            when(gradeRepository.findByStudentId(1L)).thenReturn(List.of(
                buildGrade(1L, 1L, 1L, 14.0)
            ));

            gradeMvc.perform(get("/grades?studentId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].studentId").value(1));
        }

        @Test
        @DisplayName("Étudiant sans notes → liste vide")
        void studentWithNoGrades_returnsEmpty() throws Exception {
            when(gradeRepository.findByStudentId(99L)).thenReturn(List.of());

            gradeMvc.perform(get("/grades?studentId=99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ANNONCES — POST /announcements
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /announcements — Création d'annonce")
    class CreateAnnouncement {

        @Test
        @DisplayName("Annonce valide → 201")
        void validAnnouncement_returns201() throws Exception {
            when(courseRepository.existsById(1L)).thenReturn(true);
            when(announcementRepository.save(any())).thenAnswer(inv -> {
                Announcement a = inv.getArgument(0);
                return buildAnnouncement(1L, a.getCourseId(), a.getText());
            });

            AnnouncementCreateRequest req = new AnnouncementCreateRequest();
            req.setCourseId(1L);
            req.setText("Pas de cours vendredi.");

            announceMvc.perform(post("/announcements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Pas de cours vendredi."));
        }

        @Test
        @DisplayName("Cours inexistant → 404")
        void nonExistentCourse_returns404() throws Exception {
            when(courseRepository.existsById(99L)).thenReturn(false);

            AnnouncementCreateRequest req = new AnnouncementCreateRequest();
            req.setCourseId(99L);
            req.setText("Test");

            announceMvc.perform(post("/announcements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Texte vide → 400")
        void emptyText_returns400() throws Exception {
            AnnouncementCreateRequest req = new AnnouncementCreateRequest();
            req.setCourseId(1L);
            req.setText("");

            announceMvc.perform(post("/announcements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("courseId manquant → 400")
        void missingCourseId_returns400() throws Exception {
            AnnouncementCreateRequest req = new AnnouncementCreateRequest();
            req.setText("Texte valide");

            announceMvc.perform(post("/announcements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Texte > 2000 caractères → 400")
        void textTooLong_returns400() throws Exception {
            AnnouncementCreateRequest req = new AnnouncementCreateRequest();
            req.setCourseId(1L);
            req.setText("A".repeat(2001));

            announceMvc.perform(post("/announcements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ANNONCES — GET /announcements
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /announcements — Liste des annonces")
    class ListAnnouncements {

        @Test
        @DisplayName("Sans filtre → toutes les annonces")
        void noFilter_returnsAll() throws Exception {
            when(announcementRepository.findAll()).thenReturn(List.of(
                buildAnnouncement(1L, 1L, "Annonce 1"),
                buildAnnouncement(2L, 2L, "Annonce 2")
            ));

            announceMvc.perform(get("/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("Filtre par courseId → annonces du cours uniquement")
        void filterByCourseId_returnsCourseAnnouncements() throws Exception {
            when(announcementRepository.findByCourseId(1L)).thenReturn(List.of(
                buildAnnouncement(1L, 1L, "Cours annulé")
            ));

            announceMvc.perform(get("/announcements?courseId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].text").value("Cours annulé"));
        }
    }
}
