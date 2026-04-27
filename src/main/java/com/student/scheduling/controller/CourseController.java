package com.student.scheduling.controller;

import com.student.scheduling.entity.Course;
import com.student.scheduling.repository.CourseRepository;
import com.student.scheduling.repository.DropRequestRepository;
import com.student.scheduling.repository.RegistrationRequestRepository;
import com.student.scheduling.repository.UserRepository;
import com.student.scheduling.repository.WaitlistRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Courses", description = "Course listing and admin course management endpoints")
public class CourseController {
    
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DropRequestRepository dropRequestRepository;

    @Autowired
    private RegistrationRequestRepository registrationRequestRepository;

    @Autowired
    private WaitlistRequestRepository waitlistRequestRepository;

    @GetMapping
    @Operation(summary = "List courses", description = "Public endpoint that returns all available courses.")
    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    @PostMapping
    @Operation(summary = "Create course", description = "Admin-only endpoint that creates a course.", security = @SecurityRequirement(name = "bearerAuth"))
    public Course addCourse(@RequestBody Course course) {
        return courseRepository.save(course);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update course", description = "Admin-only endpoint that updates course details.", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Course> updateCourse(@PathVariable Long id, @RequestBody Course courseDetails) {
        return courseRepository.findById(id).map(course -> {
            course.setCourseCode(courseDetails.getCourseCode());
            course.setCourseName(courseDetails.getCourseName());
            course.setInstructor(courseDetails.getInstructor());
            course.setCredits(courseDetails.getCredits());
            course.setDay(courseDetails.getDay());
            course.setStartTime(courseDetails.getStartTime());
            course.setEndTime(courseDetails.getEndTime());
            if (courseDetails.getCapacity() > 0) {
                course.setCapacity(courseDetails.getCapacity());
            }
            return ResponseEntity.ok(courseRepository.save(course));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Delete course", description = "Admin-only endpoint that deletes a course and related requests.", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        return courseRepository.findById(id).map(course -> {
            userRepository.findAll().forEach(user -> {
                if (user.getRegisteredCourses().remove(course)) {
                    userRepository.save(user);
                }
            });
            dropRequestRepository.deleteByCourseId(id);
            registrationRequestRepository.deleteByCourseId(id);
            waitlistRequestRepository.deleteByCourseId(id);
            courseRepository.delete(course);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
