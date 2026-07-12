package com.aquagreen.controller;
import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Employee;
import com.aquagreen.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/employees") @RequiredArgsConstructor
public class EmployeeController {
    private final EmployeeRepository repo;
    @GetMapping public ResponseEntity<ApiResponse<List<Employee>>> getAll() { return ResponseEntity.ok(ApiResponse.success("OK",repo.findByActiveTrueOrderByCreatedAtAsc())); }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<Employee>> getById(@PathVariable Long id) { return repo.findById(id).map(e->ResponseEntity.ok(ApiResponse.success("OK",e))).orElse(ResponseEntity.notFound().build()); }
    @PostMapping public ResponseEntity<ApiResponse<Employee>> create(@RequestBody Employee e) { e.setId(null); return ResponseEntity.ok(ApiResponse.success("Employee created",repo.save(e))); }
    @PutMapping("/{id}") public ResponseEntity<ApiResponse<Employee>> update(@PathVariable Long id,@RequestBody Employee e) { e.setId(id); return ResponseEntity.ok(ApiResponse.success("Updated",repo.save(e))); }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) { repo.findById(id).ifPresent(e->{e.setActive(false);repo.save(e);}); return ResponseEntity.ok(ApiResponse.success("Deleted",null)); }
}
