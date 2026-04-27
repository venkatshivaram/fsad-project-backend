package com.student.scheduling.controller;

import com.student.scheduling.entity.Course;
import com.student.scheduling.entity.DropRequest;
import com.student.scheduling.entity.RegistrationRequest;
import com.student.scheduling.entity.User;
import com.student.scheduling.entity.WaitlistRequest;
import com.student.scheduling.repository.CourseRepository;
import com.student.scheduling.repository.DropRequestRepository;
import com.student.scheduling.repository.RegistrationRequestRepository;
import com.student.scheduling.repository.UserRepository;
import com.student.scheduling.repository.WaitlistRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/student")
@Tag(name = "Student", description = "Student-only course, registration, waitlist, and drop request endpoints")
@SecurityRequirement(name = "bearerAuth")
public class StudentController {
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private DropRequestRepository dropRequestRepository;
    @Autowired private RegistrationRequestRepository registrationRequestRepository;
    @Autowired private WaitlistRequestRepository waitlistRequestRepository;

    @GetMapping("/{userId}/courses")
    @Operation(summary = "Get registered courses", description = "Returns the courses registered by the authenticated student.")
    public ResponseEntity<?> getRegisteredCourses(@PathVariable Long userId) {
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(user.getRegisteredCourses()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{userId}/register-request/{courseId}")
    @Operation(summary = "Request course registration", description = "Submits a course registration request or joins the waitlist when the course is full.")
    public ResponseEntity<?> requestRegistration(@PathVariable Long userId, @PathVariable Long courseId) {
        Optional<User> userOpt = userRepository.findById(userId);
        Optional<Course> courseOpt = courseRepository.findById(courseId);

        if (userOpt.isPresent() && courseOpt.isPresent()) {
            if (registrationRequestRepository.existsByStudentIdAndCourseIdAndStatus(userId, courseId, "pending")) {
                return ResponseEntity.badRequest().body(Map.of("message", "Registration request already pending"));
            }
            if (waitlistRequestRepository.existsByStudentIdAndCourseId(userId, courseId)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Already on waitlist"));
            }
            if (userOpt.get().getRegisteredCourses().contains(courseOpt.get())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Already registered for this course"));
            }

            int currentEnrolled = courseOpt.get().getEnrolledStudents() != null ? courseOpt.get().getEnrolledStudents().size() : 0;
            
            if (currentEnrolled >= courseOpt.get().getCapacity()) {
                WaitlistRequest waitlistRequest = new WaitlistRequest();
                waitlistRequest.setStudent(userOpt.get());
                waitlistRequest.setCourse(courseOpt.get());
                waitlistRequest.setRequestedAt(LocalDateTime.now());
                waitlistRequestRepository.save(waitlistRequest);
                return ResponseEntity.ok(Map.of("message", "Course full. Added to waitlist successfully", "status", "waitlisted"));
            }

            RegistrationRequest request = new RegistrationRequest();
            request.setStudent(userOpt.get());
            request.setCourse(courseOpt.get());
            request.setStatus("pending");
            request.setRequestedAt(LocalDateTime.now());
            registrationRequestRepository.save(request);

            return ResponseEntity.ok(Map.of("message", "Registration request submitted successfully"));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "User or Course not found"));
    }

    @GetMapping("/{userId}/registration-requests")
    @Operation(summary = "Get registration requests", description = "Returns registration requests for the authenticated student.")
    public ResponseEntity<?> getRegistrationRequests(@PathVariable Long userId) {
        return ResponseEntity.ok(registrationRequestRepository.findAll().stream()
                .filter(req -> req.getStudent().getId().equals(userId))
                .toList());
    }

    @GetMapping("/{userId}/waitlist-requests")
    @Operation(summary = "Get waitlist requests", description = "Returns waitlist entries for the authenticated student.")
    public ResponseEntity<?> getWaitlistRequests(@PathVariable Long userId) {
        return ResponseEntity.ok(waitlistRequestRepository.findAll().stream()
                .filter(req -> req.getStudent().getId().equals(userId))
                .toList());
    }

    @GetMapping("/{userId}/drop-requests")
    @Operation(summary = "Get drop requests", description = "Returns drop requests submitted by the authenticated student.")
    public ResponseEntity<?> getDropRequests(@PathVariable Long userId) {
        return ResponseEntity.ok(dropRequestRepository.findAll().stream()
                .filter(req -> req.getStudent().getId().equals(userId))
                .toList());
    }

    @PostMapping("/{userId}/drop-request/{courseId}")
    @Operation(summary = "Request course drop", description = "Submits a request to drop a registered course.")
    public ResponseEntity<?> requestDrop(@PathVariable Long userId, @PathVariable Long courseId) {
        Optional<User> userOpt = userRepository.findById(userId);
        Optional<Course> courseOpt = courseRepository.findById(courseId);

        if (userOpt.isPresent() && courseOpt.isPresent()) {
            if (dropRequestRepository.existsByStudentIdAndCourseIdAndStatus(userId, courseId, "pending")) {
                return ResponseEntity.badRequest().body(Map.of("message", "Drop request already pending"));
            }
            DropRequest request = new DropRequest();
            request.setStudent(userOpt.get());
            request.setCourse(courseOpt.get());
            request.setStatus("pending");
            request.setRequestedAt(LocalDateTime.now());
            dropRequestRepository.save(request);
            return ResponseEntity.ok(Map.of("message", "Drop request submitted"));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "User or Course not found"));
    }
}
