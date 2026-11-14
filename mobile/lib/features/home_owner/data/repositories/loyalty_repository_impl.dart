import 'dart:io';
import 'package:fpdart/fpdart.dart';
import '../../domain/entities/loyalty_entities.dart';
import '../../domain/failures/dashboard_failure.dart';
import '../../domain/repositories/i_loyalty_repository.dart';
import '../datasources/loyalty_remote_datasource.dart';

// ─── TenantStatus Repository ──────────────────────────────────────────────────

class TenantStatusRepositoryImpl implements ITenantStatusRepository {
  final LoyaltyRemoteDatasource _remote;
  const TenantStatusRepositoryImpl({required LoyaltyRemoteDatasource remote})
      : _remote = remote;

  @override
  Future<Either<DashboardFailure, TenantOpenStatus>> getCurrentStatus({
    required String tenantId,
    required String token,
  }) async {
    try {
      final json = await _remote.getTenantStatus(tenantId: tenantId, token: token);
      return Right(_mapStatus(json));
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(DashboardFailure.unknown(msg: e.toString()));
    }
  }

  @override
  Future<Either<DashboardFailure, TenantOpenStatus>> setOpenStatus({
    required String tenantId,
    required bool isOpen,
    required String token,
    String? reason,
  }) async {
    try {
      final json = await _remote.setTenantStatus(
        tenantId: tenantId, isOpen: isOpen, token: token, reason: reason,
      );
      return Right(_mapStatus(json));
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(DashboardFailure.unknown(msg: e.toString()));
    }
  }

  TenantOpenStatus _mapStatus(Map<String, dynamic> json) {
    final source = switch (json['source'] as String? ?? '') {
      'MANUAL_OVERRIDE' => OpenStatusSource.manualOverride,
      'SCHEDULE'        => OpenStatusSource.schedule,
      _                 => OpenStatusSource.noSchedule,
    };
    return TenantOpenStatus(
      isOpen:    json['isOpen'] as bool? ?? false,
      source:    source,
      opensAt:   json['opensAt'] as String?,
      closesAt:  json['closesAt'] as String?,
    );
  }
}

// ─── Loyalty Repository ───────────────────────────────────────────────────────

class LoyaltyRepositoryImpl implements ILoyaltyRepository {
  final LoyaltyRemoteDatasource _remote;
  const LoyaltyRepositoryImpl({required LoyaltyRemoteDatasource remote})
      : _remote = remote;

  @override
  Future<Either<DashboardFailure, LoyaltyProgram?>> getProgram({
    required String tenantId,
    required String token,
  }) async {
    try {
      final json = await _remote.getLoyaltyProgram(tenantId: tenantId, token: token);
      if (json == null) return const Right(null);
      return Right(_mapProgram(json));
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(DashboardFailure.unknown(msg: e.toString()));
    }
  }

  @override
  Future<Either<DashboardFailure, LoyaltyRule>> addRule({
    required String programId,
    required LoyaltyRule rule,
    required String token,
  }) async {
    try {
      final input = {
        'name': rule.name,
        'description': rule.description,
        'type': rule.type.name.toUpperCase(),
        'triggerValue': rule.triggerValue,
        'rewardType': rule.rewardType.name.toUpperCase(),
        'rewardValue': rule.rewardValue,
      };
      final json = await _remote.addLoyaltyRule(
        programId: programId, ruleInput: input, token: token,
      );
      return Right(_mapRule(json));
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(DashboardFailure.unknown(msg: e.toString()));
    }
  }

  @override
  Future<Either<DashboardFailure, Unit>> toggleRule({
    required String ruleId,
    required bool isActive,
    required String token,
  }) async {
    try {
      await _remote.toggleLoyaltyRule(ruleId: ruleId, isActive: isActive, token: token);
      return const Right(unit);
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(DashboardFailure.unknown(msg: e.toString()));
    }
  }

  @override
  Future<Either<DashboardFailure, LoyaltyProgram>> upsertProgram({
    required String tenantId,
    required String name,
    required String token,
    String? description,
  }) async {
    try {
      final json = await _remote.upsertLoyaltyProgram(
        name: name,
        description: description,
        token: token,
      );
      return Right(_mapProgram(json));
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(DashboardFailure.unknown(msg: e.toString()));
    }
  }

  @override
  Future<Either<DashboardFailure, List<ClientLoyaltyProgress>>> getClientProgress({
    required String tenantId,
    required String token,
  }) async {
    // El backend tiene el endpoint pero la UI de owner no lo usa aún
    // Se implementará en el CRM de clientes
    return const Right([]);
  }

  @override
  Future<Either<DashboardFailure, List<OwnerService>>> getServices({
    required String tenantId,
    required String token,
  }) async {
    try {
      final raw = await _remote.getServices(token: token);
      return Right(raw.map(_mapService).toList());
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(DashboardFailure.unknown(msg: e.toString()));
    }
  }

  @override
  Future<Either<DashboardFailure, List<BarberWithSchedule>>> getBarbersWithSchedule({
    required String tenantId,
    required String token,
  }) async {
    try {
      final raw = await _remote.getBarbersWithSchedule(token: token);
      return Right(raw.map(_mapBarber).toList());
    } on SocketException {
      return const Left(DashboardFailure.noConnection());
    } catch (e) {
      return Left(DashboardFailure.unknown(msg: e.toString()));
    }
  }

  // ─── Mappers ────────────────────────────────────────────────────────────────

  LoyaltyProgram _mapProgram(Map<String, dynamic> json) {
    final rules = (json['rules'] as List? ?? [])
        .cast<Map<String, dynamic>>()
        .map(_mapRule)
        .toList();
    return LoyaltyProgram(
      id:           json['id'] as String,
      tenantId:     json['tenantId'] as String,
      name:         json['name'] as String,
      description:  json['description'] as String?,
      rules:        rules,
      isActive:     json['isActive'] as bool? ?? true,
      totalMembers: json['totalMembers'] as int? ?? 0,
    );
  }

  LoyaltyRule _mapRule(Map<String, dynamic> json) {
    final type = switch ((json['type'] as String).toUpperCase()) {
      'VISIT_COUNT' => LoyaltyRuleType.visitCount,
      'COMBO'       => LoyaltyRuleType.combo,
      _             => LoyaltyRuleType.custom,
    };
    final rewardType = switch ((json['rewardType'] as String).toUpperCase()) {
      'FREE_SERVICE'   => LoyaltyRewardType.freeService,
      'DISCOUNT_PCT'   => LoyaltyRewardType.discountPct,
      _                => LoyaltyRewardType.discountFixed,
    };
    return LoyaltyRule(
      id:           json['id'] as String,
      programId:    json['programId'] as String,
      name:         json['name'] as String,
      description:  json['description'] as String,
      type:         type,
      triggerValue: json['triggerValue'] as int?,
      rewardType:   rewardType,
      rewardValue:  (json['rewardValue'] as num?)?.toDouble(),
      isActive:     json['isActive'] as bool? ?? true,
      sortOrder:    json['sortOrder'] as int? ?? 0,
    );
  }

  OwnerService _mapService(Map<String, dynamic> json) => OwnerService(
    id:              json['id'] as String,
    name:            json['name'] as String,
    category:        json['category'] as String,
    durationMinutes: json['durationMinutes'] as int,
    basePrice:       json['basePrice'] as String,
    depositType:     json['depositType'] as String,
    depositAmount:   json['depositAmount'] as String? ?? '0.00',
    maxConcurrent:   json['maxConcurrent'] as int? ?? 1,
    isActive:        json['isActive'] as bool? ?? true,
  );

  BarberWithSchedule _mapBarber(Map<String, dynamic> json) {
    final schedule = json['schedule'] as Map<String, dynamic>?;
    return BarberWithSchedule(
      memberId:       json['id'] as String,
      name:           json['name'] as String,
      isWorkingToday: schedule?['isWorking'] as bool? ?? false,
      openTime:       schedule?['openTime'] as String?,
      closeTime:      schedule?['closeTime'] as String?,
      capacity:       schedule?['capacity'] as int? ?? 1,
    );
  }
}
