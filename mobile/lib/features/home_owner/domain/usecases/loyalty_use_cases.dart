import 'package:fpdart/fpdart.dart';
import '../entities/loyalty_entities.dart';
import '../failures/dashboard_failure.dart';
import '../repositories/i_loyalty_repository.dart';

// ─── Apertura ─────────────────────────────────────────────────────────────────

class GetTenantStatusUseCase {
  final ITenantStatusRepository _repo;
  const GetTenantStatusUseCase(this._repo);

  Future<Either<DashboardFailure, TenantOpenStatus>> call({
    required String tenantId,
    required String token,
  }) => _repo.getCurrentStatus(tenantId: tenantId, token: token);
}

class ToggleTenantOpenUseCase {
  final ITenantStatusRepository _repo;
  const ToggleTenantOpenUseCase(this._repo);

  Future<Either<DashboardFailure, TenantOpenStatus>> call({
    required String tenantId,
    required bool isOpen,
    required String token,
    String? reason,
  }) => _repo.setOpenStatus(
    tenantId: tenantId, isOpen: isOpen, token: token, reason: reason,
  );
}

// ─── Fidelización ─────────────────────────────────────────────────────────────

class GetLoyaltyProgramUseCase {
  final ILoyaltyRepository _repo;
  const GetLoyaltyProgramUseCase(this._repo);

  Future<Either<DashboardFailure, LoyaltyProgram?>> call({
    required String tenantId,
    required String token,
  }) => _repo.getProgram(tenantId: tenantId, token: token);
}

class AddLoyaltyRuleUseCase {
  final ILoyaltyRepository _repo;
  const AddLoyaltyRuleUseCase(this._repo);

  Future<Either<DashboardFailure, LoyaltyRule>> call({
    required String programId,
    required LoyaltyRule rule,
    required String token,
  }) => _repo.addRule(programId: programId, rule: rule, token: token);
}

class ToggleLoyaltyRuleUseCase {
  final ILoyaltyRepository _repo;
  const ToggleLoyaltyRuleUseCase(this._repo);

  Future<Either<DashboardFailure, Unit>> call({
    required String ruleId,
    required bool isActive,
    required String token,
  }) => _repo.toggleRule(ruleId: ruleId, isActive: isActive, token: token);
}

// ─── Datos del home ───────────────────────────────────────────────────────────

class GetOwnerServicesUseCase {
  final ILoyaltyRepository _repo;
  const GetOwnerServicesUseCase(this._repo);

  Future<Either<DashboardFailure, List<OwnerService>>> call({
    required String tenantId,
    required String token,
  }) => _repo.getServices(tenantId: tenantId, token: token);
}

class GetBarbersWithScheduleUseCase {
  final ILoyaltyRepository _repo;
  const GetBarbersWithScheduleUseCase(this._repo);

  Future<Either<DashboardFailure, List<BarberWithSchedule>>> call({
    required String tenantId,
    required String token,
  }) => _repo.getBarbersWithSchedule(tenantId: tenantId, token: token);
}
