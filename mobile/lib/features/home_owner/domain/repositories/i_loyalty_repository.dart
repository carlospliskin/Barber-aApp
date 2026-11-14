import 'package:fpdart/fpdart.dart';
import '../entities/loyalty_entities.dart';
import '../failures/dashboard_failure.dart';

// ─── Repositorio de estado de apertura ───────────────────────────────────────

abstract interface class ITenantStatusRepository {
  /// Estado actual de la barbería (híbrido horario + override)
  Future<Either<DashboardFailure, TenantOpenStatus>> getCurrentStatus({
    required String tenantId,
    required String token,
  });

  /// Override manual: abrir o cerrar la barbería
  Future<Either<DashboardFailure, TenantOpenStatus>> setOpenStatus({
    required String tenantId,
    required bool isOpen,
    required String token,
    String? reason,
  });
}

// ─── Repositorio de fidelización ─────────────────────────────────────────────

abstract interface class ILoyaltyRepository {
  /// Obtener el programa de la barbería (incluye reglas)
  Future<Either<DashboardFailure, LoyaltyProgram?>> getProgram({
    required String tenantId,
    required String token,
  });

  /// Crear o actualizar programa
  Future<Either<DashboardFailure, LoyaltyProgram>> upsertProgram({
    required String tenantId,
    required String name,
    required String token,
    String? description,
  });

  /// Agregar regla al programa
  Future<Either<DashboardFailure, LoyaltyRule>> addRule({
    required String programId,
    required LoyaltyRule rule,
    required String token,
  });

  /// Activar/desactivar regla
  Future<Either<DashboardFailure, Unit>> toggleRule({
    required String ruleId,
    required bool isActive,
    required String token,
  });

  /// Progreso de todos los clientes
  Future<Either<DashboardFailure, List<ClientLoyaltyProgress>>> getClientProgress({
    required String tenantId,
    required String token,
  });

  /// Servicios del owner para el grid y para crear reglas
  Future<Either<DashboardFailure, List<OwnerService>>> getServices({
    required String tenantId,
    required String token,
  });

  /// Barberos con su horario de hoy
  Future<Either<DashboardFailure, List<BarberWithSchedule>>> getBarbersWithSchedule({
    required String tenantId,
    required String token,
  });
}
